package com.campuslens.model;

public record LikeResponse(Long checkInId, boolean liked, int likeCount) {
}
