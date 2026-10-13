package com.ordersphere.audit.web;

import java.util.List;

public record AuditPageResponse(List<AuditEntryResponse> items, int page, int size, long total) {}
