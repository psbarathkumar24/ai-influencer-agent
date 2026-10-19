package com.aiinfluencer.repository;

import com.aiinfluencer.domain.entity.SocialMediaAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SocialMediaAccountRepository extends JpaRepository<SocialMediaAccount, Long> {
    List<SocialMediaAccount> findByUserId(Long userId);
    Optional<SocialMediaAccount> findByUserIdAndPlatform(Long userId, SocialMediaAccount.Platform platform);
    List<SocialMediaAccount> findByUserIdAndIsConnected(Long userId, Boolean isConnected);
}
