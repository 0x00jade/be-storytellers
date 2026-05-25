package com.demo.bestorytellers.social.repository;

import com.demo.bestorytellers.social.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, Follow.FollowId> {

    boolean existsByIdFollowerIdAndIdFollowingId(UUID followerId, UUID followingId);

    void deleteByIdFollowerIdAndIdFollowingId(UUID followerId, UUID followingId);

    long countByIdFollowingId(UUID followingId);

    long countByIdFollowerId(UUID followerId);

    @Query("SELECT f.id.followingId FROM Follow f WHERE f.id.followerId = :followerId")
    List<UUID> findFollowingIds(@Param("followerId") UUID followerId);
}
