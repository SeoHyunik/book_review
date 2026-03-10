package com.example.macronews.service.auth;

import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnonymousDetailViewGateService {

    private static final String SESSION_KEY = "anonymousViewedNewsDetailIds";

    private final int freeDetailViews;

    public AnonymousDetailViewGateService(@Value("${app.gating.free-detail-views:3}") int freeDetailViews) {
        this.freeDetailViews = Math.max(freeDetailViews, 0);
    }

    public boolean canAccess(String newsId, HttpSession session) {
        if (!StringUtils.hasText(newsId)) {
            return false;
        }
        Set<String> viewedIds = getViewedIds(session);
        return viewedIds.contains(newsId) || viewedIds.size() < freeDetailViews;
    }

    public void recordAccess(String newsId, HttpSession session) {
        if (!StringUtils.hasText(newsId)) {
            return;
        }
        Set<String> viewedIds = getViewedIds(session);
        viewedIds.add(newsId);
        session.setAttribute(SESSION_KEY, viewedIds);
    }

    public int getFreeDetailViews() {
        return freeDetailViews;
    }

    @SuppressWarnings("unchecked")
    private Set<String> getViewedIds(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof Set<?> ids) {
            Set<String> typed = new LinkedHashSet<>();
            for (Object id : ids) {
                if (id instanceof String value && StringUtils.hasText(value)) {
                    typed.add(value);
                }
            }
            return typed;
        }
        return new LinkedHashSet<>();
    }
}
