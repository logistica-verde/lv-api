package br.com.greenvrp.route;

import java.util.List;

public record OperationPageResponse(List<OperationSummaryResponse> items, long total, int page, int size) {
}
