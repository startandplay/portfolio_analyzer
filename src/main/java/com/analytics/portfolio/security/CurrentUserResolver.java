package com.analytics.portfolio.security;

import com.analytics.portfolio.model.User;
import com.analytics.portfolio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolve o utilizador "atual" para os controllers.
 *
 * Em produção (segurança ativa): usa sempre o user vindo de
 * @AuthenticationPrincipal, que nunca é null porque o endpoint
 * exige autenticação.
 *
 * Em fase de testes (SecurityConfig com permitAll()):
 * @AuthenticationPrincipal chega sempre null porque não há
 * autenticação a popular o SecurityContext. Este resolver faz
 * fallback para um "dev user" fixo, configurável via properties,
 * para que os endpoints continuem a funcionar sem login.
 *
 * IMPORTANTE: o fallback só deve estar ativo em dev/test.
 * Quando a segurança for reativada em produção, app.dev-mode=false
 * desativa o fallback e qualquer pedido sem token autenticado
 * falha como seria de esperar.
 *
 * application.properties (dev):
 *   app.dev-mode=true
 *   app.dev-user-id=1
 *
 * application.properties (prod):
 *   app.dev-mode=false
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    @Value("${app.dev-user-id:1}")
    private Long devUserId;

    /**
     * Resolve o user atual.
     *
     * @param authenticatedUser o user injetado por @AuthenticationPrincipal
     *                          (pode ser null se a segurança estiver desativada)
     * @return o user real, ou o dev user de fallback se devMode=true
     * @throws ResponseStatusException 401 se não houver user e devMode=false
     */
    public User resolve(User authenticatedUser) {
        if (authenticatedUser != null) {
            return authenticatedUser;
        }

        if (!devMode) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        log.warn("DEV MODE: nenhum user autenticado — usando fallback userId={}. " +
                "Define app.dev-mode=false em produção!", devUserId);

        return userRepository.findById(devUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Dev user (id=" + devUserId + ") not found. " +
                                "Create a user with this ID or change app.dev-user-id."));
    }
}