
# 보안

## 📖 개요
대규모 트래픽 환경에서 애플리케이션과 데이터를 보호하기 위한 보안 전략과 구현

## 🎯 학습 목표
- 강력한 인증/인가 시스템 구축
- 데이터 보호 및 암호화 구현
- API 보안 best practices 적용

---

## 1. 인증과 인가

### JWT 기반 인증
```java
@Component
public class JwtTokenProvider {
    @Value(\"${jwt.secret}\")
    private String secretKey;
    
    @Value(\"${jwt.expiration}\")
    private long validityInMilliseconds;
    
    @PostConstruct
    protected void init() {
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }
    
    public String createToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put(\"sub\", userPrincipal.getId());
        claims.put(\"email\", userPrincipal.getEmail());
        claims.put(\"roles\", userPrincipal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList()));
        
        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(validity)
            .signWith(SignatureAlgorithm.HS512, secretKey)
            .compact();
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            return true;
        } catch (SignatureException ex) {
            log.error(\"Invalid JWT signature\");
        } catch (MalformedJwtException ex) {
            log.error(\"Invalid JWT token\");
        } catch (ExpiredJwtException ex) {
            log.error(\"Expired JWT token\");
        } catch (UnsupportedJwtException ex) {
            log.error(\"Unsupported JWT token\");
        } catch (IllegalArgumentException ex) {
            log.error(\"JWT claims string is empty\");
        }
        return false;
    }
}
```

### OAuth 2.0 통합
```java
@Configuration
@EnableWebSecurity
public class OAuth2SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(\"/api/public/**\").permitAll()
                .requestMatchers(\"/api/admin/**\").hasRole(\"ADMIN\")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authorization -> authorization
                    .baseUri(\"/oauth2/authorize\")
                    .authorizationRequestRepository(cookieAuthorizationRequestRepository())
                )
                .redirectionEndpoint(redirection -> redirection
                    .baseUri(\"/oauth2/callback/*\")
                )
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(oAuth2AuthenticationSuccessHandler)
                .failureHandler(oAuth2AuthenticationFailureHandler)
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        
        return http.build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
}
```

### RBAC (Role-Based Access Control)
```java
@Component
public class RBACService {
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @PreAuthorize(\"hasRole('ADMIN')\")
    public void assignRole(Long userId, String roleName) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new RoleNotFoundException(roleName));
        
        user.getRoles().add(role);
        userRepository.save(user);
    }
    
    @PreAuthorize(\"@rbacService.hasPermission(#resource, #action)\")
    public void performAction(String resource, String action) {
        // 권한이 있는 경우에만 실행
        log.info(\"Performing {} on {}\", action, resource);
    }
    
    public boolean hasPermission(String resource, String action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(authority -> {
                Permission permission = permissionRepository
                    .findByResourceAndAction(resource, action);
                return permission != null && 
                    permission.getRoles().stream()
                        .anyMatch(role -> authority.equals(\"ROLE_\" + role.getName()));
            });
    }
}
```

---

## 2. API 보안

### Rate Limiting과 DDoS 방어
```java
@Component
public class ApiSecurityFilter extends OncePerRequestFilter {
    @Autowired
    private RateLimiter rateLimiter;
    
    @Autowired
    private IpBlacklistService blacklistService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        
        // IP 블랙리스트 체크
        if (blacklistService.isBlocked(clientIp)) {
            response.sendError(HttpStatus.FORBIDDEN.value(), \"Access denied\");
            return;
        }
        
        // Rate limiting
        String apiKey = request.getHeader(\"X-API-Key\");
        String rateLimitKey = apiKey != null ? apiKey : clientIp;
        
        if (!rateLimiter.allowRequest(rateLimitKey)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(\"X-RateLimit-Limit\", \"100\");
            response.setHeader(\"X-RateLimit-Remaining\", \"0\");
            response.setHeader(\"X-RateLimit-Reset\", 
                String.valueOf(System.currentTimeMillis() + 60000));
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader(\"X-Forwarded-For\");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(\",\")[0].trim();
        }
        
        String xRealIp = request.getHeader(\"X-Real-IP\");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
```

### API Key 관리
```java
@Service
public class ApiKeyService {
    @Autowired
    private ApiKeyRepository apiKeyRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    public ApiKey generateApiKey(String clientName, Set<String> scopes) {
        String rawKey = generateSecureRandomKey();
        String hashedKey = passwordEncoder.encode(rawKey);
        
        ApiKey apiKey = new ApiKey();
        apiKey.setClientName(clientName);
        apiKey.setKeyHash(hashedKey);
        apiKey.setScopes(scopes);
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setExpiresAt(LocalDateTime.now().plusYears(1));
        apiKey.setActive(true);
        
        apiKeyRepository.save(apiKey);
        
        // 실제 키는 한 번만 반환
        apiKey.setRawKey(rawKey);
        return apiKey;
    }
    
    public boolean validateApiKey(String rawKey) {
        String keyPrefix = rawKey.substring(0, 8);
        
        Optional<ApiKey> apiKey = apiKeyRepository.findByKeyPrefix(keyPrefix);
        
        if (apiKey.isEmpty() || !apiKey.get().isActive()) {
            return false;
        }
        
        if (apiKey.get().getExpiresAt().isBefore(LocalDateTime.now())) {
			return false;
        }
        
        return passwordEncoder.matches(rawKey, apiKey.get().getKeyHash());
    }
    
    private String generateSecureRandomKey() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
```

### CORS 설정
```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 허용된 출처
        configuration.setAllowedOrigins(Arrays.asList(
            "https://app.example.com",
            "https://admin.example.com"
        ));
        
        // 허용된 메서드
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));
        
        // 허용된 헤더
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-API-Key",
            "X-Request-ID"
        ));
        
        // 노출할 헤더
        configuration.setExposedHeaders(Arrays.asList(
            "X-Total-Count",
            "X-Page-Number",
            "X-Page-Size"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = 
            new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }
}
```

---

## 3. 데이터 보호

### 암호화
```java
@Component
public class EncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    
    @Value("${encryption.master-key}")
    private String masterKey;
    
    private SecretKey secretKey;
    
    @PostConstruct
    public void init() {
        byte[] decodedKey = Base64.getDecoder().decode(masterKey);
        secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }
    
    public String encrypt(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        
        // IV 생성
        byte[] iv = new byte[IV_LENGTH_BYTE];
        new SecureRandom().nextBytes(iv);
        
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        
        // 암호화
        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // IV + 암호문 결합
        byte[] cipherTextWithIv = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, cipherTextWithIv, 0, iv.length);
        System.arraycopy(cipherText, 0, cipherTextWithIv, iv.length, cipherText.length);
        
        return Base64.getEncoder().encodeToString(cipherTextWithIv);
    }
    
    public String decrypt(String ciphertext) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(ciphertext);
        
        // IV 추출
        byte[] iv = new byte[IV_LENGTH_BYTE];
        System.arraycopy(decoded, 0, iv, 0, iv.length);
        
        // 암호문 추출
        byte[] cipherText = new byte[decoded.length - iv.length];
        System.arraycopy(decoded, iv.length, cipherText, 0, cipherText.length);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        
        byte[] plainText = cipher.doFinal(cipherText);
        return new String(plainText, StandardCharsets.UTF_8);
    }
}
```

### 민감 데이터 마스킹
```java
@Component
public class DataMaskingService {
    
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];
        
        if (localPart.length() <= 3) {
            return "***@" + domain;
        }
        
        return localPart.substring(0, 3) + "***@" + domain;
    }
    
    public String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 10) {
            return phoneNumber;
        }
        
        return phoneNumber.substring(0, 3) + "****" + 
               phoneNumber.substring(phoneNumber.length() - 3);
    }
    
    public String maskCreditCard(String creditCard) {
        if (creditCard == null || creditCard.length() < 12) {
            return creditCard;
        }
        
        String cleaned = creditCard.replaceAll("[^0-9]", "");
        return "**** **** **** " + cleaned.substring(cleaned.length() - 4);
    }
}

// Jackson 직렬화 시 자동 마스킹
@JsonSerialize(using = MaskingSerializer.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Masked {
    MaskType value() default MaskType.FULL;
}

public class MaskingSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, 
                         SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        
        // 마스킹 로직
        String masked = "***" + value.substring(Math.max(0, value.length() - 4));
        gen.writeString(masked);
    }
}
```

---

## 4. 보안 헤더

### Security Headers 설정
```java
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        // XSS 방지
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        // 클릭재킹 방지
        response.setHeader("X-Frame-Options", "DENY");
        
        // MIME 타입 스니핑 방지
        response.setHeader("X-Content-Type-Options", "nosniff");
        
        // HTTPS 강제
        response.setHeader("Strict-Transport-Security", 
            "max-age=31536000; includeSubDomains; preload");
        
        // 콘텐츠 보안 정책
        response.setHeader("Content-Security-Policy", 
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://cdn.example.com; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https:; " +
            "font-src 'self' https://fonts.gstatic.com; " +
            "connect-src 'self' https://api.example.com; " +
            "frame-ancestors 'none';");
        
        // Referrer 정책
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // 권한 정책
        response.setHeader("Permissions-Policy", 
            "camera=(), microphone=(), geolocation=()");
        
        filterChain.doFilter(request, response);
    }
}
```

---

## 5. SQL Injection 방지

### 안전한 쿼리 작성
```java
@Repository
public class UserRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // 파라미터 바인딩 사용
    public List<User> findUsersByName(String name) {
        String sql = "SELECT * FROM users WHERE name = ?";
        return jdbcTemplate.query(sql, new Object[]{name}, new UserRowMapper());
    }
    
    // Named Parameters 사용
    public List<User> findUsersByCriteria(UserSearchCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1=1");
        
        if (criteria.getName() != null) {
            sql.append(" AND name LIKE :name");
            params.addValue("name", "%" + criteria.getName() + "%");
        }
        
        if (criteria.getEmail() != null) {
            sql.append(" AND email = :email");
            params.addValue("email", criteria.getEmail());
        }
        
        return namedParameterJdbcTemplate.query(
            sql.toString(), params, new UserRowMapper()
        );
    }
    
    // JPA Criteria API 사용
    public List<User> findUsersSecurely(String searchTerm) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        Predicate predicate = cb.or(
            cb.like(root.get("name"), "%" + searchTerm + "%"),
            cb.like(root.get("email"), "%" + searchTerm + "%")
        );
        
        query.where(predicate);
        return entityManager.createQuery(query).getResultList();
    }
}
```

---

## 6. 보안 감사 (Audit)

### 감사 로깅
```java
@Component
@Aspect
public class AuditAspect {
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, 
                       Auditable auditable) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";
        
        AuditLog auditLog = new AuditLog();
        auditLog.setUsername(username);
        auditLog.setAction(auditable.action());
        auditLog.setResource(auditable.resource());
        auditLog.setTimestamp(LocalDateTime.now());
        auditLog.setIpAddress(getClientIp());
        
        try {
            Object result = joinPoint.proceed();
            auditLog.setStatus("SUCCESS");
            auditLog.setDetails(extractDetails(joinPoint, result));
            return result;
        } catch (Exception e) {
            auditLog.setStatus("FAILURE");
            auditLog.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            auditLogRepository.save(auditLog);
        }
    }
    
    private String extractDetails(ProceedingJoinPoint joinPoint, Object result) {
        Map<String, Object> details = new HashMap<>();
        
        // 메서드 파라미터
        Object[] args = joinPoint.getArgs();
        String[] paramNames = ((MethodSignature) joinPoint.getSignature())
            .getParameterNames();
        
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null && !isSensitive(paramNames[i])) {
                details.put(paramNames[i], args[i].toString());
            }
        }
        
        // 결과 요약
        if (result != null) {
            details.put("result", result.getClass().getSimpleName());
        }
        
        return new ObjectMapper().writeValueAsString(details);
    }
    
    private boolean isSensitive(String paramName) {
        return paramName.toLowerCase().contains("password") ||
               paramName.toLowerCase().contains("secret") ||
               paramName.toLowerCase().contains("token");
    }
}
```

### 이상 탐지
```java
@Component
public class SecurityAnomalyDetector {
    private final Map<String, UserActivityProfile> userProfiles = 
        new ConcurrentHashMap<>();
    
    @EventListener
    public void detectAnomalies(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        String location = extractLocation(event);
        LocalDateTime loginTime = LocalDateTime.now();
        
        UserActivityProfile profile = userProfiles.computeIfAbsent(
            username, k -> new UserActivityProfile()
        );
        
        // 비정상적인 위치에서 로그인
        if (!profile.isNormalLocation(location)) {
            sendSecurityAlert("Unusual login location", username, location);
        }
        
        // 비정상적인 시간대 로그인
        if (!profile.isNormalLoginTime(loginTime)) {
            sendSecurityAlert("Unusual login time", username, loginTime.toString());
        }
        
        // 짧은 시간 내 여러 위치에서 로그인
        if (profile.hasMultipleLocationsRecently()) {
            sendSecurityAlert("Multiple locations detected", username, 
                profile.getRecentLocations().toString());
        }
        
        profile.recordLogin(location, loginTime);
    }
    
    @Scheduled(fixedDelay = 60000) // 1분마다
    public void detectBruteForceAttempts() {
        Map<String, Long> failedAttempts = authenticationFailureRepository
            .countFailedAttemptsInLastMinute();
        
        failedAttempts.forEach((ip, count) -> {
            if (count > 5) {
                blockIpAddress(ip);
                sendSecurityAlert("Possible brute force attack", ip, 
                    "Failed attempts: " + count);
            }
        });
    }
}
```

---

## 7. 보안 테스트

### 보안 테스트 자동화
```java
@SpringBootTest
@AutoConfigureMockMvc
public class SecurityTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    public void testSqlInjection() throws Exception {
        String maliciousInput = "'; DROP TABLE users; --";
        
        mockMvc.perform(get("/api/users")
                .param("name", maliciousInput))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
        
        // 테이블이 여전히 존재하는지 확인
        assertTableExists("users");
    }
    
    @Test
    public void testXssProtection() throws Exception {
        String xssPayload = "<script>alert('XSS')</script>";
        
        UserDto user = new UserDto();
        user.setName(xssPayload);
        
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(not(containsString("<script>"))));
    }
    
    @Test
    public void testRateLimiting() throws Exception {
        String apiKey = "test-api-key";
        
        // 제한 내에서 요청
        for (int i = 0; i < 100; i++) {
            mockMvc.perform(get("/api/data")
                    .header("X-API-Key", apiKey))
                .andExpect(status().isOk());
        }
        
        // 제한 초과
        mockMvc.perform(get("/api/data")
                .header("X-API-Key", apiKey))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("X-RateLimit-Remaining"))
            .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }
}
```

---

## 📚 참고 자료
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security in Action - Laurentiu Spilca]
- [Java Security Guidelines](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

## ✅ 체크포인트
- [ ] JWT 기반 인증 구현
- [ ] API Rate Limiting 구현
- [ ] 데이터 암호화 및 마스킹
- [ ] SQL Injection 방지 대책
- [ ] 보안 감사 및 모니터링
- [ ] 보안 테스트 자동화

## 🔗 추가 학습 리소스
- [[Resources|추천 도서 및 강의]]
- [[Projects|실습 프로젝트]]
- [[Tools|필수 도구 목록]]
