package com.pb.synth.tradecapture.testutil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test fixtures and sample data for testing.
 */
public class TestFixtures {

    public static final String DEFAULT_TRADE_ID = "TRADE-2024-001";
    public static final String DEFAULT_ACCOUNT_ID = "ACC-001";
    public static final String DEFAULT_BOOK_ID = "BOOK-001";
    public static final String DEFAULT_SECURITY_ID = "US0378331005";
    public static final String DEFAULT_PARTITION_KEY = "ACC-001_BOOK-001_US0378331005";

    /**
     * Creates a sample trade lot for testing.
     */
    public static Map<String, Object> createSampleTradeLot() {
        Map<String, Object> tradeLot = new HashMap<>();
        
        List<Map<String, Object>> lotIdentifiers = new ArrayList<>();
        Map<String, Object> identifier = new HashMap<>();
        identifier.put("identifier", "LOT-001");
        identifier.put("identifierType", "INTERNAL");
        lotIdentifiers.add(identifier);
        tradeLot.put("lotIdentifier", lotIdentifiers);

        List<Map<String, Object>> priceQuantities = new ArrayList<>();
        Map<String, Object> priceQuantity = new HashMap<>();
        
        List<Map<String, Object>> quantities = new ArrayList<>();
        Map<String, Object> quantity = new HashMap<>();
        quantity.put("value", 10000.0);
        Map<String, Object> unit = new HashMap<>();
        unit.put("financialUnit", "Shares");
        quantity.put("unit", unit);
        quantities.add(quantity);
        priceQuantity.put("quantity", quantities);
        
        List<Map<String, Object>> prices = new ArrayList<>();
        Map<String, Object> price = new HashMap<>();
        price.put("value", 150.50);
        Map<String, Object> priceUnit = new HashMap<>();
        priceUnit.put("currency", "USD");
        price.put("unit", priceUnit);
        prices.add(price);
        priceQuantity.put("price", prices);
        
        priceQuantities.add(priceQuantity);
        tradeLot.put("priceQuantity", priceQuantities);

        return tradeLot;
    }

    /**
     * Creates sample economic terms for testing.
     */
    public static Map<String, Object> createSampleEconomicTerms() {
        Map<String, Object> economicTerms = new HashMap<>();
        economicTerms.put("effectiveDate", LocalDate.now().toString());
        economicTerms.put("terminationDate", LocalDate.now().plusYears(1).toString());
        
        List<Map<String, Object>> payouts = new ArrayList<>();
        economicTerms.put("payout", payouts);
        
        return economicTerms;
    }

    /**
     * Creates sample performance payout for testing.
     */
    public static Map<String, Object> createSamplePerformancePayout() {
        Map<String, Object> payout = new HashMap<>();
        Map<String, Object> performancePayout = new HashMap<>();
        
        Map<String, Object> payerReceiver = new HashMap<>();
        payerReceiver.put("payer", "CPTY-001");
        payerReceiver.put("receiver", "CPTY-002");
        performancePayout.put("payerReceiver", payerReceiver);
        
        Map<String, Object> underlier = new HashMap<>();
        Map<String, Object> observable = new HashMap<>();
        Map<String, Object> asset = new HashMap<>();
        Map<String, Object> security = new HashMap<>();
        List<Map<String, Object>> identifiers = new ArrayList<>();
        Map<String, Object> secId = new HashMap<>();
        secId.put("identifier", DEFAULT_SECURITY_ID);
        secId.put("identifierType", "ISIN");
        identifiers.add(secId);
        security.put("identifier", identifiers);
        asset.put("security", security);
        observable.put("asset", asset);
        underlier.put("observable", observable);
        performancePayout.put("underlier", underlier);
        
        payout.put("performancePayout", performancePayout);
        return payout;
    }

    /**
     * Creates sample interest payout for testing.
     */
    public static Map<String, Object> createSampleInterestPayout() {
        Map<String, Object> payout = new HashMap<>();
        Map<String, Object> interestPayout = new HashMap<>();
        
        Map<String, Object> payerReceiver = new HashMap<>();
        payerReceiver.put("payer", "CPTY-002");
        payerReceiver.put("receiver", "CPTY-001");
        interestPayout.put("payerReceiver", payerReceiver);
        
        interestPayout.put("fixedRate", 0.025);
        interestPayout.put("dayCountFraction", "ACT/360");
        
        payout.put("interestRatePayout", interestPayout);
        return payout;
    }

    /**
     * Creates sample state for testing.
     */
    public static Map<String, Object> createSampleState(String positionState) {
        Map<String, Object> state = new HashMap<>();
        state.put("positionState", positionState);
        return state;
    }

    /**
     * Creates sample rule for testing.
     */
    public static Map<String, Object> createSampleRule(String ruleId, String ruleType, String target) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("id", ruleId);
        rule.put("name", "Test Rule " + ruleId);
        rule.put("description", "Test rule description");
        rule.put("ruleType", ruleType);
        rule.put("target", target);
        rule.put("priority", 100);
        rule.put("enabled", true);
        
        List<Map<String, Object>> criteria = new ArrayList<>();
        Map<String, Object> criterion = new HashMap<>();
        criterion.put("field", "trade.source");
        criterion.put("operator", "EQUALS");
        criterion.put("value", "AUTOMATED");
        criteria.add(criterion);
        rule.put("criteria", criteria);
        
        List<Map<String, Object>> actions = new ArrayList<>();
        Map<String, Object> action = new HashMap<>();
        action.put("type", "SET_FIELD");
        action.put("target", "workflowStatus");
        action.put("value", "APPROVED");
        actions.add(action);
        rule.put("actions", actions);
        
        return rule;
    }

    /**
     * Creates sample security data for testing.
     */
    public static Map<String, Object> createSampleSecurity() {
        Map<String, Object> security = new HashMap<>();
        security.put("securityId", DEFAULT_SECURITY_ID);
        security.put("isin", DEFAULT_SECURITY_ID);
        security.put("assetClass", "Equity");
        security.put("currency", "USD");
        security.put("riskRating", "LOW");
        return security;
    }

    /**
     * Creates sample account data for testing.
     */
    public static Map<String, Object> createSampleAccount() {
        Map<String, Object> account = new HashMap<>();
        account.put("accountId", DEFAULT_ACCOUNT_ID);
        account.put("bookId", DEFAULT_BOOK_ID);
        account.put("type", "INSTITUTIONAL");
        account.put("status", "OPEN");
        account.put("legalEntity", "LE-001");
        return account;
    }
}

