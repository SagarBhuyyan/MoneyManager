package in.bushansirgur.moneymanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import in.bushansirgur.moneymanager.entity.IncomeEntity;
import in.bushansirgur.moneymanager.entity.ExpenseEntity;
import in.bushansirgur.moneymanager.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final ProfileRepository profileRepository;
    
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    
    @Value("${gemini.project.id}")
    private String projectId;
    
    @Value("${gemini.location:us-central1}")
    private String location;
    
    @Value("${gemini.model:gemini-1.5-flash}")
    private String modelName;
    
    public Map<String, Object> getFinancialAnalysis(Long profileId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("Generating AI financial analysis for profile: {}", profileId);
            
            // Test Gemini connection first
            if (!testGeminiConnection()) {
                result.put("success", false);
                result.put("error", "Gemini API connection failed. Please check configuration.");
                result.put("analysis", getFallbackAnalysis(profileId));
                return result;
            }
            
            // Get financial data
            Map<String, Object> financialData = prepareFinancialData(profileId);
            log.info("Financial data prepared: {}", financialData.size() + " data points");
            
            // Generate AI analysis
            String analysis = generateAIAnalysis(financialData);
            log.info("AI analysis generated successfully");
            
            // Parse the analysis into structured format
            Map<String, Object> parsedAnalysis = parseAnalysis(analysis);
            
            result.put("success", true);
            result.put("analysis", parsedAnalysis);
            result.put("rawData", financialData);
            result.put("timestamp", new Date());
            result.put("aiModel", modelName);
            
        } catch (Exception e) {
            log.error("Error generating financial analysis for profile {}: {}", profileId, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            
            // Fallback to basic analysis
            result.put("analysis", getFallbackAnalysis(profileId));
        }
        
        return result;
    }
    
    public boolean testGeminiConnection() {
        try {
            if (geminiApiKey == null || geminiApiKey.isEmpty()) {
                log.error("Gemini API key is not configured");
                return false;
            }
            
            if (projectId == null || projectId.isEmpty()) {
                log.error("Gemini project ID is not configured");
                return false;
            }
            
            log.info("Testing Gemini connection with project: {}, location: {}", projectId, location);
            
            // Simple test prompt
            String testPrompt = "Hello, respond with 'OK' if you can hear me.";
            
            VertexAI vertexAi = new VertexAI.Builder()
                .setProjectId(projectId)
                .setLocation(location)
                .build();
            
            GenerativeModel model = new GenerativeModel.Builder()
                .setModelName(modelName)
                .setVertexAi(vertexAi)
                .build();
            
            GenerateContentResponse response = model.generateContent(testPrompt);
            String result = response.getCandidates(0).getContent().getParts(0).getText();
            
            vertexAi.close();
            
            log.info("Gemini connection test successful: {}", result);
            return true;
            
        } catch (Exception e) {
            log.error("Gemini connection test failed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private Map<String, Object> prepareFinancialData(Long profileId) {
        Map<String, Object> data = new HashMap<>();
        
        try {
            // Get profile
            var profile = profileRepository.findById(profileId).orElse(null);
            if (profile == null) {
                throw new RuntimeException("Profile not found for ID: " + profileId);
            }
            
            // Use getFullName() instead of getName()
            String profileName = profile.getFullName() != null ? profile.getFullName() : "User";
            data.put("profileName", profileName);
            data.put("currency", "₹");
            data.put("profileId", profileId);
            
            // Get last 6 months of data
            LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
            
            // Get income data - use multiple fallback strategies
            List<IncomeEntity> incomes = getIncomeData(profileId, sixMonthsAgo);
            
            // Get expense data - use multiple fallback strategies
            List<ExpenseEntity> expenses = getExpenseData(profileId, sixMonthsAgo);
            
            // Monthly breakdown
            Map<String, BigDecimal> monthlyIncome = new TreeMap<>();
            Map<String, BigDecimal> monthlyExpense = new TreeMap<>();
            Map<String, BigDecimal> categoryExpenses = new HashMap<>();
            
            DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy");
            
            // Process incomes
            for (IncomeEntity income : incomes) {
                if (income.getDate() != null && income.getAmount() != null) {
                    String month = income.getDate().format(monthFormatter);
                    monthlyIncome.merge(month, income.getAmount(), BigDecimal::add);
                }
            }
            
            // Process expenses
            for (ExpenseEntity expense : expenses) {
                if (expense.getDate() != null && expense.getAmount() != null) {
                    String month = expense.getDate().format(monthFormatter);
                    monthlyExpense.merge(month, expense.getAmount(), BigDecimal::add);
                    
                    // Category breakdown
                    String category = expense.getCategory() != null ? 
                        expense.getCategory().getName() : "Uncategorized";
                    categoryExpenses.merge(category, expense.getAmount(), BigDecimal::add);
                }
            }
            
            // Calculate totals
            BigDecimal totalIncome = incomes.stream()
                    .map(IncomeEntity::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalExpense = expenses.stream()
                    .map(ExpenseEntity::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal netBalance = totalIncome.subtract(totalExpense);
            
            // Calculate savings rate
            BigDecimal savingsRate = BigDecimal.ZERO;
            if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
                savingsRate = totalIncome.subtract(totalExpense)
                    .divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            }
            
            // Prepare structured data
            data.put("totalIncome", totalIncome);
            data.put("totalExpense", totalExpense);
            data.put("netBalance", netBalance);
            data.put("savingsRate", savingsRate);
            data.put("monthlyIncome", monthlyIncome);
            data.put("monthlyExpense", monthlyExpense);
            data.put("categoryExpenses", categoryExpenses);
            data.put("incomeCount", incomes.size());
            data.put("expenseCount", expenses.size());
            data.put("analysisPeriod", "Last 6 months");
            data.put("dataFrom", sixMonthsAgo.toString());
            data.put("dataTo", LocalDate.now().toString());
            
            // Get top 5 expenses
            List<Map<String, Object>> topExpenses = expenses.stream()
                .filter(exp -> exp.getAmount() != null)
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .limit(5)
                .map(exp -> {
                    Map<String, Object> expenseMap = new HashMap<>();
                    expenseMap.put("name", exp.getName());
                    expenseMap.put("amount", exp.getAmount());
                    expenseMap.put("date", exp.getDate() != null ? exp.getDate().toString() : "Unknown");
                    expenseMap.put("category", exp.getCategory() != null ? exp.getCategory().getName() : "Uncategorized");
                    return expenseMap;
                })
                .collect(Collectors.toList());
            
            data.put("topExpenses", topExpenses);
            
            // Calculate trends
            if (monthlyIncome.size() >= 2) {
                List<BigDecimal> incomeValues = new ArrayList<>(monthlyIncome.values());
                BigDecimal latestIncome = incomeValues.get(incomeValues.size() - 1);
                BigDecimal previousIncome = incomeValues.get(incomeValues.size() - 2);
                
                if (previousIncome.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal incomeGrowth = latestIncome.subtract(previousIncome)
                            .divide(previousIncome, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                    data.put("incomeGrowth", incomeGrowth);
                }
            }
            
        } catch (Exception e) {
            log.error("Error preparing financial data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to prepare financial data: " + e.getMessage());
        }
        
        return data;
    }
    
    private List<IncomeEntity> getIncomeData(Long profileId, LocalDate sixMonthsAgo) {
        try {
            // Method 1: Try findByProfileIdAndDateBetween
            return incomeRepository.findByProfileIdAndDateBetween(profileId, sixMonthsAgo, LocalDate.now());
        } catch (Exception e1) {
            log.warn("Method 1 for income failed: {}", e1.getMessage());
            try {
                // Method 2: Get all incomes and filter manually
                List<IncomeEntity> allIncomes = incomeRepository.findByProfileId(profileId);
                return allIncomes.stream()
                    .filter(income -> income.getDate() != null && !income.getDate().isBefore(sixMonthsAgo))
                    .collect(Collectors.toList());
            } catch (Exception e2) {
                log.warn("Method 2 for income failed: {}", e2.getMessage());
                // Method 3: Use latest incomes as fallback
                return incomeRepository.findTop5ByProfileIdOrderByDateDesc(profileId);
            }
        }
    }
    
    private List<ExpenseEntity> getExpenseData(Long profileId, LocalDate sixMonthsAgo) {
        try {
            // Method 1: Try findByProfileIdAndDateBetweenOrderByDateDesc
            return expenseRepository.findByProfileIdAndDateBetweenOrderByDateDesc(profileId, sixMonthsAgo, LocalDate.now());
        } catch (Exception e1) {
            log.warn("Method 1 for expenses failed: {}", e1.getMessage());
            try {
                // Method 2: Get all expenses and filter manually
                List<ExpenseEntity> allExpenses = expenseRepository.findByProfileIdOrderByDateDesc(profileId);
                return allExpenses.stream()
                    .filter(expense -> expense.getDate() != null && !expense.getDate().isBefore(sixMonthsAgo))
                    .collect(Collectors.toList());
            } catch (Exception e2) {
                log.warn("Method 2 for expenses failed: {}", e2.getMessage());
                // Method 3: Use latest expenses as fallback
                return expenseRepository.findTop5ByProfileIdOrderByDateDesc(profileId);
            }
        }
    }
    
    private String generateAIAnalysis(Map<String, Object> financialData) throws IOException {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new RuntimeException("Gemini API key not configured");
        }
        
        log.info("Calling Gemini API with project: {}, location: {}", projectId, location);
        
        try {
            // Prepare the prompt
            String prompt = buildPrompt(financialData);
            log.debug("Generated prompt length: {}", prompt.length());
            
            // Initialize Vertex AI
            VertexAI vertexAi = new VertexAI.Builder()
                .setProjectId(projectId)
                .setLocation(location)
                .build();
            
            GenerativeModel model = new GenerativeModel.Builder()
                .setModelName(modelName)
                .setVertexAi(vertexAi)
                .build();
            
            log.info("Sending request to Gemini API...");
            GenerateContentResponse response = model.generateContent(prompt);
            
            String analysis = response.getCandidates(0).getContent().getParts(0).getText();
            log.info("Received response from Gemini API, length: {}", analysis.length());
            
            vertexAi.close();
            return analysis;
            
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            throw new IOException("Failed to call Gemini API: " + e.getMessage(), e);
        }
    }
    
    private String buildPrompt(Map<String, Object> financialData) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonData = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(financialData);
            
            return String.format("""
                You are an expert financial advisor specializing in personal finance management. 
                Analyze the following financial data and provide detailed insights and recommendations.
                
                IMPORTANT: You MUST respond with VALID JSON in the exact format specified below.
                Do not include any markdown, code blocks, or additional text outside the JSON.
                
                Financial Data:
                %s
                
                Required JSON Response Format:
                {
                  "overallAssessment": "A brief 2-3 sentence assessment of the user's financial health",
                  "financialHealthScore": 85,
                  "keyInsights": [
                    "First key insight about spending patterns",
                    "Second key insight about savings",
                    "Third key insight about income trends"
                  ],
                  "monthlyAnalysis": {
                    "bestMonth": "Month with highest savings",
                    "worstMonth": "Month with highest expenses",
                    "trend": "Increasing/Decreasing/Stable"
                  },
                  "categoryAnalysis": {
                    "topSpendingCategory": "Category where most money is spent",
                    "recommendedCategoryToReduce": "Category where spending can be reduced",
                    "savingsOpportunity": 5000
                  },
                  "recommendations": [
                    {
                      "title": "Actionable recommendation title",
                      "description": "Detailed description of the recommendation",
                      "priority": "High/Medium/Low"
                    }
                  ],
                  "riskAlerts": [
                    {
                      "type": "Spending Alert",
                      "message": "Specific alert message",
                      "severity": "Warning/Danger/Info"
                    }
                  ],
                  "predictedSavings": 15000,
                  "nextMonthForecast": {
                    "expectedIncome": 50000,
                    "expectedExpenses": 35000,
                    "expectedSavings": 15000
                  }
                }
                
                Guidelines:
                1. All amounts should be in Indian Rupees (₹)
                2. Be specific, actionable, and practical
                3. Focus on Indian financial context and realities
                4. Provide realistic numbers based on the data
                5. FinancialHealthScore should be 0-100 based on savings rate, spending patterns, and consistency
                """, jsonData);
                
        } catch (Exception e) {
            log.error("Failed to build prompt: {}", e.getMessage());
            // Fallback to simple prompt
            return String.format("""
                Analyze this financial data and provide insights in JSON format: %s
                
                Provide response in valid JSON with: overallAssessment, financialHealthScore (0-100), 
                keyInsights (array), recommendations (array), and nextMonthForecast.
                """, financialData.toString());
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAnalysis(String analysis) {
        try {
            // Clean the response (remove markdown code blocks if present)
            String cleanedAnalysis = analysis.trim();
            if (cleanedAnalysis.startsWith("```json")) {
                cleanedAnalysis = cleanedAnalysis.substring(7);
            }
            if (cleanedAnalysis.startsWith("```")) {
                cleanedAnalysis = cleanedAnalysis.substring(3);
            }
            if (cleanedAnalysis.endsWith("```")) {
                cleanedAnalysis = cleanedAnalysis.substring(0, cleanedAnalysis.length() - 3);
            }
            cleanedAnalysis = cleanedAnalysis.trim();
            
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> result = mapper.readValue(cleanedAnalysis, Map.class);
            log.info("Successfully parsed AI analysis JSON");
            return result;
            
        } catch (Exception e) {
            log.warn("Failed to parse AI analysis as JSON: {}", e.getMessage());
            log.debug("Raw analysis content: {}", analysis);
            
            // Return as text analysis
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("textAnalysis", analysis);
            fallback.put("overallAssessment", "AI analysis completed. Some formatting issues occurred.");
            fallback.put("financialHealthScore", 75);
            fallback.put("keyInsights", Arrays.asList(
                "Analysis generated successfully",
                "Review your spending patterns regularly",
                "Consider increasing your savings rate"
            ));
            fallback.put("recommendations", Arrays.asList(
                Map.of("title", "Check AI Configuration", 
                       "description", "Ensure Gemini API is properly configured",
                       "priority", "High")
            ));
            return fallback;
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getFallbackAnalysis(Long profileId) {
        Map<String, Object> fallback = new HashMap<>();
        
        try {
            // Basic analysis without AI
            List<IncomeEntity> incomes = incomeRepository.findByProfileId(profileId);
            List<ExpenseEntity> expenses = expenseRepository.findByProfileIdOrderByDateDesc(profileId);
            
            BigDecimal totalIncome = incomes.stream()
                    .map(IncomeEntity::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalExpense = expenses.stream()
                    .map(ExpenseEntity::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal savings = totalIncome.subtract(totalExpense);
            BigDecimal savingsRate = totalIncome.compareTo(BigDecimal.ZERO) > 0 
                    ? savings.divide(totalIncome, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;
            
            // Calculate basic health score
            int healthScore = 70;
            if (savingsRate.compareTo(new BigDecimal("20")) > 0) healthScore = 85;
            if (savingsRate.compareTo(new BigDecimal("10")) < 0) healthScore = 60;
            if (savings.compareTo(BigDecimal.ZERO) < 0) healthScore = 40;
            
            fallback.put("overallAssessment", "Basic financial analysis. Enable AI for personalized insights and recommendations.");
            fallback.put("financialHealthScore", healthScore);
            fallback.put("keyInsights", Arrays.asList(
                String.format("Total Income: ₹%,.2f", totalIncome),
                String.format("Total Expenses: ₹%,.2f", totalExpense),
                String.format("Net Savings: ₹%,.2f", savings),
                String.format("Savings Rate: %.1f%%", savingsRate)
            ));
            fallback.put("recommendations", Arrays.asList(
                Map.of("title", "Configure Gemini AI", 
                       "description", "Set up your Gemini API key in the application properties for detailed AI-powered financial insights",
                       "priority", "High"),
                Map.of("title", "Track Expenses Regularly",
                       "description", "Maintain consistent expense tracking to identify spending patterns",
                       "priority", "Medium")
            ));
            fallback.put("nextMonthForecast", Map.of(
                "expectedIncome", totalIncome.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP),
                "expectedExpenses", totalExpense.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP),
                "expectedSavings", savings.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP)
            ));
            
        } catch (Exception e) {
            log.error("Error in fallback analysis: {}", e.getMessage());
            fallback.put("error", "Unable to generate analysis. Please check your data and configuration.");
        }
        
        return fallback;
    }
}