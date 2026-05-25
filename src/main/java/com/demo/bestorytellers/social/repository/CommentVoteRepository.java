package com.demo.bestorytellers.social.repository;

import com.demo.bestorytellers.social.entity.CommentVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommentVoteRepository extends JpaRepository<CommentVote, CommentVote.CommentVoteId> {

    Optional<CommentVote> findByIdCommentIdAndIdUserId(UUID commentId, UUID userId);
}
