package com.campuslens.model;

public record ReplyLikeResponse(Long replyId, boolean liked, int likeCount) {
}
