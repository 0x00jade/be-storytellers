package com.demo.bestorytellers.social.repository;

import com.demo.bestorytellers.social.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query(value = "SELECT c FROM Comment c WHERE c.chapter.id = :chapterId AND c.parent IS NULL ORDER BY c.createdAt DESC",
           countQuery = "SELECT COUNT(c) FROM Comment c WHERE c.chapter.id = :chapterId AND c.parent IS NULL")
    Page<Comment> findTopLevelByChapterId(@Param("chapterId") UUID chapterId, Pageable pageable);

    @Query(value = "SELECT c FROM Comment c WHERE c.parent.id = :parentId ORDER BY c.createdAt ASC",
           countQuery = "SELECT COUNT(c) FROM Comment c WHERE c.parent.id = :parentId")
    Page<Comment> findByParentIdOrderByCreatedAtAsc(@Param("parentId") UUID parentId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.parent.id = :commentId")
    long countReplies(@Param("commentId") UUID commentId);
}
