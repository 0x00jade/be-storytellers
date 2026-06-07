package com.demo.bestorytellers.story.repository;

import com.demo.bestorytellers.story.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Integer> {

    Optional<Tag> findBySlug(String slug);

    Optional<Tag> findByName(String name);

    List<Tag> findByNameContainingIgnoreCaseOrSlugContainingIgnoreCase(String name, String slug);

}
