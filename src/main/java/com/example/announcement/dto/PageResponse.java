package com.example.announcement.dto;

import java.util.List;

public class PageResponse<T> {

	private List<T> items;
	private int page;
	private int size;
	private long totalItems;
	private int totalPages;

	public PageResponse() {
	}

	public PageResponse(List<T> items, int page, int size, long totalItems, int totalPages) {
		this.items = items;
		this.page = page;
		this.size = size;
		this.totalItems = totalItems;
		this.totalPages = totalPages;
	}

	public List<T> getItems() {
		return items;
	}

	public int getPage() {
		return page;
	}

	public int getSize() {
		return size;
	}

	public long getTotalItems() {
		return totalItems;
	}

	public int getTotalPages() {
		return totalPages;
	}
}
