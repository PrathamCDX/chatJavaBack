package com.example.demo.dto;

import java.util.List;

/**
 * Upload progress of a message's file. Parts are numbered 0 to parts - 1; both lists are in ascending order.
 * uploaded can include numbers outside that range if a client sent them, missing never does.
 */
public record FilePartsResponse(Long fileId, Long parts, List<Long> uploaded, List<Long> missing) {
}
