/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Wraps a Spring Data {@link Page} with pagination metadata for API responses.
 *
 * <p>Provides clients with page position, total counts, and navigation flags
 * alongside the actual content array. Used by all paginated list endpoints.</p>
 *
 * <pre>{@code
 * {
 *   "content": [ ... ],
 *   "page": 0,
 *   "size": 10,
 *   "totalElements": 47,
 *   "totalPages": 5,
 *   "first": true,
 *   "last": false
 * }
 * }</pre>
 *
 * @param <T> the type of content (e.g., TaskResponse)
 */
@Schema(description = "Paginated response wrapper with metadata")
public class PagedResponse<T> {

    // =========================================================================
    // FIELDS — Pagination Metadata
    // =========================================================================
    // Imagine a book with 47 pages of tasks, 10 tasks per page.
    // If the client asks for page 0:
    //   content:        tasks 1-10      (the actual data for THIS page)
    //   page:           0               (first page, 0-based index)
    //   size:           10              (how many per page the client asked for)
    //   totalElements:  47              (total tasks in the database)
    //   totalPages:     5               (47 tasks ÷ 10 per page = 5 pages)
    //   first:          true            (this IS the first page)
    //   last:           false           (there ARE more pages after this)

    @Schema(description = "Items for the current page")
    private List<T> content;

    @Schema(description = "Current page number (0-based)", example = "0")
    private int page;

    @Schema(description = "Number of items per page", example = "10")
    private int size;

    @Schema(description = "Total number of items across all pages", example = "47")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "5")
    private int totalPages;

    @Schema(description = "Whether this is the first page", example = "true")
    private boolean first;

    @Schema(description = "Whether this is the last page", example = "false")
    private boolean last;

    // =========================================================================
    // PRIVATE CONSTRUCTOR + STATIC FACTORY METHOD
    // =========================================================================
    // The constructor is private — you CANNOT do "new PagedResponse()".
    // You MUST use PagedResponse.from(page).
    //
    // Why? Because creating a PagedResponse requires extracting data from a
    // Spring Data Page object. The factory method ensures this is done
    // correctly every time.

    /** Private — use {@link #from(Page)} factory method. */
    private PagedResponse() {}

    /**
     * Creates a PagedResponse from a Spring Data {@link Page}.
     *
     * <p>Spring Data's Page object is the standard way to get paginated
     * results from a database query. It contains both the content AND
     * all the metadata (page number, total count, etc.). This factory
     * method copies that data into our clean DTO.</p>
     *
     * @param page the Spring Data Page from a repository query
     * @param <T>  the type of content (inferred from the Page)
     * @return a new PagedResponse with all fields populated
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        PagedResponse<T> response = new PagedResponse<>();
        // Copy the actual list of items for this page
        response.content = page.getContent();
        // Page number: 0-based (0 = first page, 1 = second page, etc.)
        response.page = page.getNumber();
        // How many items the client asked for per page
        response.size = page.getSize();
        // Total count across ALL pages (not just this one)
        response.totalElements = page.getTotalElements();
        // How many pages exist in total
        response.totalPages = page.getTotalPages();
        // Navigation flags: used to show/hide Previous/Next buttons
        response.first = page.isFirst();
        response.last = page.isLast();
        return response;
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    public List<T> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public boolean isFirst() { return first; }
    public boolean isLast() { return last; }
}