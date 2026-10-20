package com.udea.digitalbank.shared.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Entrega el {@link Actor} de la petición. El principal es el id del usuario (sub del JWT), que deja
 * JwtAuthenticationFilter; el rol, la autoridad ROLE_ADMIN. Es el único lugar donde se lee Authentication.
 */
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentActor.class) && Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Los endpoints con @CurrentActor exigen autenticación; si llega aquí sin ella es un error de configuración
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new IllegalStateException("@CurrentActor en un endpoint sin usuario autenticado");
        }
        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
        return new Actor(userId, admin);
    }
}
