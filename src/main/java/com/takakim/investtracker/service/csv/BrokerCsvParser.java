package com.takakim.investtracker.service.csv;

import java.util.List;

public interface BrokerCsvParser {
    String getBrokerName();
    boolean supports(List<String> headerColumns);
    List<ParsedTransactionRow> parse(String csvContent);
}
