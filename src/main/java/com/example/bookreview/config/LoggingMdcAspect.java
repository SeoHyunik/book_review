package com.example.bookreview.config;

import org.apache.logging.log4j.ThreadContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingMdcAspect {

    @Around("@within(org.springframework.stereotype.Controller) || @within(org.springframework.web.bind.annotation.RestController)")
    public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        return withLayer("MVC", joinPoint);
    }

    @Around("@within(org.springframework.stereotype.Service)")
    public Object aroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        return withLayer("SERVICE", joinPoint);
    }

    @Around("@within(org.springframework.stereotype.Repository)")
    public Object aroundRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        return withLayer("REPO", joinPoint);
    }

    private Object withLayer(String layer, ProceedingJoinPoint joinPoint) throws Throwable {
        String previousLayer = ThreadContext.get("layer");
        ThreadContext.put("layer", layer);
        try {
            return joinPoint.proceed();
        } finally {
            if (previousLayer == null) {
                ThreadContext.remove("layer");
            } else {
                ThreadContext.put("layer", previousLayer);
            }
        }
    }
}
