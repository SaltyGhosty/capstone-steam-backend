package com.capstone.steam.wishlist;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.StorefrontRules;
import com.capstone.steam.user.UserRepository;
import com.capstone.steam.wishlist.dto.WishlistItemResponse;

@Service
public class WishlistService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "addedAt");

    private final WishlistItemRepository wishlistRepository;
    private final UserRepository userRepository;
    private final StorefrontRules storefrontRules;

    public WishlistService(WishlistItemRepository wishlistRepository, UserRepository userRepository,
            StorefrontRules storefrontRules) {
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.storefrontRules = storefrontRules;
    }

    @Transactional(readOnly = true)
    public PageResponse<WishlistItemResponse> list(Long userId, Pageable pageable) {
        var page = wishlistRepository.findByUserId(userId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
        return PageResponse.of(page, WishlistItemResponse::from);
    }

    /** Idempotente. */
    @Transactional
    public void add(Long userId, Long gameId) {
        Game game = storefrontRules.requireAcquirable(userId, gameId);
        if (!wishlistRepository.existsByUserIdAndGameId(userId, gameId)) {
            wishlistRepository.save(new WishlistItem(userRepository.getReferenceById(userId), game));
        }
    }

    /** Idempotente. */
    @Transactional
    public void remove(Long userId, Long gameId) {
        wishlistRepository.deleteByUserIdAndGameId(userId, gameId);
    }
}
