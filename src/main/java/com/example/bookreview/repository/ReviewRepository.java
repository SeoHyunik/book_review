package com.example.bookreview.repository;

import com.example.bookreview.dto.domain.Review;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {

    List<Review> findByOwnerUserId(String ownerUserId);

    Optional<Review> findByIdAndOwnerUserId(String id, String ownerUserId);
}
