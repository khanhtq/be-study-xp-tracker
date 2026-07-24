package com.studytracker.repository;

import com.studytracker.model.Friendship;
import com.studytracker.model.FriendshipStatus;
import com.studytracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester = :u1 AND f.addressee = :u2) OR " +
           "(f.requester = :u2 AND f.addressee = :u1)")
    Optional<Friendship> findBetweenUsers(@Param("u1") User u1, @Param("u2") User u2);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester.id = :u1Id AND f.addressee.id = :u2Id) OR " +
           "(f.requester.id = :u2Id AND f.addressee.id = :u1Id)")
    Optional<Friendship> findBetweenUserIds(@Param("u1Id") UUID u1Id, @Param("u2Id") UUID u2Id);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester = :user OR f.addressee = :user) AND f.status = :status")
    List<Friendship> findAllByUserAndStatus(@Param("user") User user, @Param("status") FriendshipStatus status);

    List<Friendship> findByAddresseeAndStatusOrderByCreatedAtDesc(User addressee, FriendshipStatus status);

    List<Friendship> findByRequesterAndStatusOrderByCreatedAtDesc(User requester, FriendshipStatus status);

    @Query("SELECT COUNT(f) > 0 FROM Friendship f WHERE " +
           "((f.requester.id = :u1Id AND f.addressee.id = :u2Id) OR " +
           " (f.requester.id = :u2Id AND f.addressee.id = :u1Id)) AND " +
           "f.status = 'ACCEPTED'")
    boolean isFriends(@Param("u1Id") UUID u1Id, @Param("u2Id") UUID u2Id);

    long countByAddresseeAndStatus(User addressee, FriendshipStatus status);
}
