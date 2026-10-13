package com.codearena.api.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequests {

    public static final int MAX_PAGE_SIZE = 50;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort);
    }
}
