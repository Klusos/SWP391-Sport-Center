package group5.example.sportCenter.service;

record CounterReceiptRecordingResult(MembershipUpgradeSettlementResult.Outcome outcome,
        Integer receiptId, String reason) {}
