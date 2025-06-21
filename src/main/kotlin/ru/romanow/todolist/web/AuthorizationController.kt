package ru.romanow.todolist.web

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie.from
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.romanow.todolist.config.properties.OAuthLoginProperties

@Hidden
@RestController
@RequestMapping("/callback")
class AuthorizationController(
    private val oauthLoginProperties: OAuthLoginProperties,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun items(token: OAuth2AuthenticationToken): ResponseEntity<Void> {
        val tokenCookie = from("access_token", (token.principal as DefaultOidcUser).idToken.tokenValue)
            .path("/")
            .httpOnly(false)
            .maxAge(36000)
            .build()

        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, oauthLoginProperties.redirectUri)
            .header(HttpHeaders.SET_COOKIE, tokenCookie.toString())
            .build()
    }
}
