# Q3: Authentication & Roles

**Branch:** `feature/q3-auth` · **Time:** 25 min · **PR title:** `Q3: Authentication & Roles`

## Requirements (from the assignment)
- Users can **register** and **log in**. Store passwords **securely**.
- Used by web and mobile clients, so authentication **must not rely on server-side sessions**.
- Login **expires after 15 minutes**.
- Two roles, **USER** and **ADMIN**. Any logged-in user can view **their own profile**. Only an **ADMIN** can **list all users**.
- Not logged in → **401**. Logged in without the right role → **403**. Both return **JSON**, not an HTML error page.

## Acceptance criteria
- [ ] A test proves that a USER cannot access the admin endpoint.
- [ ] No secrets are hard-coded in the source.

**Optional:** stay logged in beyond 15 minutes without re-entering the password (refresh token), plus a logout that ends it.

---

## Recommended design

### Dependencies (add on this branch)
- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-resource-server`: brings Nimbus JOSE + the built-in `BearerTokenAuthenticationFilter`, so there's no hand-written JWT filter.
- `spring-security-test` (test scope)

### Data
`V3__create_users.sql`: `id`, `email VARCHAR(255) UNIQUE NOT NULL`, `password_hash VARCHAR(100) NOT NULL`, `role VARCHAR(20) NOT NULL`, `created_at`.

### API
| Method | Path | Access | Success | Errors |
|---|---|---|---|---|
| POST | `/api/auth/register` `{ email, password }` | public | 201 `{ id, email, role }` | 400, 409 email taken |
| POST | `/api/auth/login` `{ email, password }` | public | 200 `{ accessToken, tokenType: "Bearer", expiresIn: 900 }` | 401 |
| GET | `/api/users/me` | any authenticated user | 200 profile | 401 |
| GET | `/api/admin/users` | ADMIN | 200 list | 401, 403 |

### Security config
```java
http.csrf(csrf -> csrf.disable())                       // no cookies, so no CSRF risk
    .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
    .authorizeHttpRequests(a -> a
        .requestMatchers("/api/auth/**").permitAll()
        .requestMatchers(GET, "/{code:[A-Za-z0-9]{1,8}}").permitAll()   // Q2 redirect stays public
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated())
    .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter())))
    .exceptionHandling(e -> e
        .authenticationEntryPoint(jsonEntryPoint)          // 401 as ApiError JSON
        .accessDeniedHandler(jsonAccessDeniedHandler));    // 403 as ApiError JSON
```
- **Token:** HS256 JWT signed with `JwtEncoder` (`NimbusJwtEncoder`). Claims are `sub`=email, `roles`=[USER], `iat`, and `exp`=now+15m.
- **Decoder:** `NimbusJwtDecoder.withSecretKey(...)`. Set `JwtTimestampValidator(Duration.ZERO)` so expiry is exactly 15 minutes. The default allows **60 seconds of clock skew**, which is a good interview point.
- **Secret:** `app.jwt.secret: ${JWT_SECRET}`, read from the environment, with no default in `application.yml`. Tests generate a random 256-bit key via `@DynamicPropertySource` or set it in `src/test/resources/application.yml` (a test-only value, which you should be ready to justify).
- **Passwords:** `BCryptPasswordEncoder` (adaptive, salted). Never log or return the hash.
- **Registering as ADMIN:** impossible through the API, because the role is always USER. The admin is seeded on startup from `ADMIN_EMAIL`/`ADMIN_PASSWORD` env vars if they're set.

### ⚠️ Impact on Q1 and Q2
Once security is on the classpath, **every endpoint requires a token**, so the Q1/Q2 tests will start failing with 401. Decide which endpoints stay public (the redirect does, `/api/**` doesn't), and update the existing tests with `.with(jwt())` or `@WithMockUser`. Call this out in the PR.

### Decisions & alternatives
| Decision | Chosen | Alternatives | Why |
|---|---|---|---|
| Stateless auth | JWT access token | Server sessions + cookie (forbidden by the spec) · opaque tokens + DB lookup each request | No server state, and works for mobile. Trade-off: it can't be revoked before expiry. |
| JWT library | Spring's resource server (Nimbus) | jjwt + a custom `OncePerRequestFilter` | Less custom security code. Validation of signature and expiry is battle-tested. |
| Signing | HS256 shared secret | RS256 key pair | One service signs and verifies, so a symmetric key is enough. RS256 is better when other services verify. |
| Password hash | BCrypt | Argon2, PBKDF2 | Spring default, salted, adaptive cost. |
| 401/403 JSON | `AuthenticationEntryPoint` + `AccessDeniedHandler` | `@RestControllerAdvice` | Security exceptions happen **in the filter chain, before the DispatcherServlet**, so controller advice never sees them. |
| Login failure message | Generic "Invalid credentials" | "User not found" vs "wrong password" | Prevents user enumeration. |

## Test plan
| AC | Test | Type |
|---|---|---|
| **USER can't access admin** | `AdminUsersTest.listUsers_asUser_returns403Json` | `@SpringBootTest` + MockMvc, real token from login (or `jwt().authorities(ROLE_USER)`) |
| No token | `listUsers_noToken_returns401Json` | MockMvc |
| ADMIN ok | `listUsers_asAdmin_returns200` | MockMvc |
| Me | `me_withToken_returnsOwnProfile` | MockMvc |
| Expired token | `me_expiredToken_returns401` | Encode a token with exp in the past |
| Password stored hashed | `register_storesBcryptHash` | repository assert, the hash `startsWith("$2")` |

## Commit plan
1. `Add user entity, migration and registration with BCrypt`
2. `Issue 15-minute JWT on login`
3. `Secure endpoints with stateless JWT and role rules`
4. `Return JSON for 401 and 403`
5. `Update existing tests to authenticate`
6. `Add role access tests`

## Interview prep
- **Request flow:** `Authorization: Bearer x` → `BearerTokenAuthenticationFilter` → `JwtDecoder` verifies the signature and `exp` → `JwtAuthenticationConverter` maps `roles` to `ROLE_*` authorities → `SecurityContext` → `AuthorizationFilter` checks the `hasRole` rules → controller (`@AuthenticationPrincipal Jwt jwt` gives you `sub`).
- **How does logout work with JWT?** There's no server state, so the token stays valid until `exp`. Options: short expiry (what we have), a deny-list of `jti` until expiry, or refresh tokens stored in the DB and revoked on logout (the bonus).
- **What if you remove `STATELESS`?** Spring may create an `HttpSession` and `JSESSIONID` cookie, which violates the requirement.
- **Why is disabling CSRF OK?** CSRF exploits browsers automatically sending cookies. Bearer tokens in a header aren't sent automatically.
- **Where's the secret?** It comes from the environment variable `JWT_SECRET`, and the app fails fast at startup if it's missing.
- **Two concurrent registrations with the same email?** Both pass the "exists?" check. The UNIQUE constraint rejects the second, which we map to 409.
- **Live change ideas:** change expiry to 5 minutes; add a `MODERATOR` role; make `/api/users/{id}` accessible to the owner or an ADMIN (`@PreAuthorize`).
