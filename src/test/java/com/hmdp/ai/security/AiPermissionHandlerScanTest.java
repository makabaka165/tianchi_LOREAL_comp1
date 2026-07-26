package com.hmdp.ai.security;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hmdp.ai.api.security.RequireAiPermission;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPermissionHandlerScanTest {
    /**
     * Pre-scope endpoints that intentionally require only login: they run before a
     * tenant/workspace scope exists, are excluded from AiPermissionInterceptor in
     * MvcConfig, and must still declare {@link SaCheckLogin} explicitly.
     */
    private static final Set<String> LOGIN_ONLY_ALLOWLIST = Set.of(
            "com.hmdp.ai.api.session.SessionController#bootstrap");

    @Test
    void everyV1HandlerDeclaresAnExplicitPermission() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> missing = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("com.hmdp.ai.api")) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller,
                    RequestMapping.class);
            String[] classPaths = classMapping == null ? new String[0] : paths(classMapping);
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method,
                        RequestMapping.class);
                if (methodMapping == null) continue;
                if (!isV1(classPaths, paths(methodMapping))) continue;
                RequireAiPermission permission = AnnotatedElementUtils.findMergedAnnotation(method,
                        RequireAiPermission.class);
                if (permission == null) {
                    permission = AnnotatedElementUtils.findMergedAnnotation(controller,
                            RequireAiPermission.class);
                }
                if (permission == null && isDeclaredLoginOnly(controller, method)) continue;
                if (permission == null) missing.add(controller.getName() + "#" + method.getName());
            }
        }
        assertTrue(missing.isEmpty(), "V1 handlers without explicit permission: " + missing);
    }

    private boolean isDeclaredLoginOnly(Class<?> controller, Method method) {
        if (!LOGIN_ONLY_ALLOWLIST.contains(controller.getName() + "#" + method.getName())) {
            return false;
        }
        return AnnotatedElementUtils.findMergedAnnotation(method, SaCheckLogin.class) != null;
    }

    private boolean isV1(String[] classPaths, String[] methodPaths) {
        for (String classPath : classPaths) {
            if (classPath.startsWith("/api/v1")) return true;
            for (String methodPath : methodPaths) {
                if ((classPath + methodPath).startsWith("/api/v1")) return true;
            }
        }
        if (classPaths.length == 0) {
            for (String methodPath : methodPaths) if (methodPath.startsWith("/api/v1")) return true;
        }
        return false;
    }

    private String[] paths(RequestMapping mapping) {
        return mapping.path().length == 0 ? mapping.value() : mapping.path();
    }
}
