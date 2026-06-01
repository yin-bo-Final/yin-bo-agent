package com.yinbo.agent.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.auth.AuthConstants;
import com.yinbo.agent.auth.dto.AuthUserView;
import com.yinbo.agent.auth.dto.CurrentUserResponse;
import com.yinbo.agent.auth.dto.DeleteAccountRequest;
import com.yinbo.agent.auth.dto.LoginRequest;
import com.yinbo.agent.auth.dto.LoginResponse;
import com.yinbo.agent.auth.dto.LogoutResponse;
import com.yinbo.agent.auth.dto.RegisterRequest;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.auth.mapper.AuthUserMapper;
import com.yinbo.agent.auth.session.LoginUser;
import com.yinbo.agent.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
// 基于 Spring Session 的认证服务实现。
public class SessionAuthService implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthService.class);
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

    private final AuthUserMapper authUserMapper;
    private final PasswordEncoder passwordEncoder;

    // 注入用户 Mapper 和密码编码器。
    public SessionAuthService(AuthUserMapper authUserMapper, PasswordEncoder passwordEncoder) {
        this.authUserMapper = authUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    // 注册普通用户并创建登录 Session。
    public LoginResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        String username = normalizeUsername(request.username());
        ensureUsernameAvailable(username);

        AuthUser authUser = new AuthUser();
        authUser.setUsername(username);
        authUser.setPasswordHash(passwordEncoder.encode(request.password()));
        authUser.setDisplayName(username);
        authUser.setRole(ROLE_USER);
        authUser.setStatus(1);

        try {
            authUserMapper.insert(authUser);
        } catch (DuplicateKeyException exception) {
            log.warn("event=user_register_failed username={} reason=duplicate_username", sanitizeLogValue(username));
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已被占用，请换一个");
        }

        LoginResponse response = createLoginSession(authUser, httpRequest);
        log.info(
                "event=user_registered userId={} username={} role={}",
                authUser.getId(),
                sanitizeLogValue(authUser.getUsername()),
                authUser.getRole()
        );
        return response;
    }

    @Override
    @Transactional
    // 校验用户名密码并创建登录 Session。
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = normalizeUsername(request.username());
        AuthUser authUser;
        try {
            authUser = findSingleActiveUserByUsername(username);
        } catch (BusinessException exception) {
            log.warn("event=user_login_failed username={} reason=user_not_available", sanitizeLogValue(username));
            throw exception;
        }

        if (!passwordEncoder.matches(request.password(), authUser.getPasswordHash())) {
            log.warn(
                    "event=user_login_failed userId={} username={} reason=bad_credentials",
                    authUser.getId(),
                    sanitizeLogValue(authUser.getUsername())
            );
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        LoginResponse response = createLoginSession(authUser, httpRequest);
        log.info(
                "event=user_login_success userId={} username={} role={}",
                authUser.getId(),
                sanitizeLogValue(authUser.getUsername()),
                authUser.getRole()
        );
        return response;
    }

    // 创建新的登录 Session 并写入 gateway 限流需要的用户 ID。
    private LoginResponse createLoginSession(AuthUser authUser, HttpServletRequest httpRequest) {
        authUser.setLastLoginAt(LocalDateTime.now());
        authUserMapper.updateById(authUser);

        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        LoginUser loginUser = new LoginUser(
                authUser.getId(),
                authUser.getUsername(),
                authUser.getDisplayName(),
                Instant.now()
        );

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(AuthConstants.LOGIN_USER_SESSION_KEY, loginUser);
        session.setAttribute(AuthConstants.LOGIN_USER_ID_SESSION_KEY, authUser.getId());

        return new LoginResponse(session.getId(), loginUser.loginAt(), toView(authUser));
    }

    @Override
    // 返回当前登录用户信息。
    public CurrentUserResponse currentUser(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        LoginUser loginUser = session == null
                ? null
                : (LoginUser) session.getAttribute(AuthConstants.LOGIN_USER_SESSION_KEY);
        AuthUser authUser = requireActiveUser(httpRequest);
        return new CurrentUserResponse(session.getId(), loginUser.loginAt(), toView(authUser));
    }

    @Override
    // 退出登录并销毁当前 Session。
    public LogoutResponse logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        LoginUser loginUser = session == null
                ? null
                : (LoginUser) session.getAttribute(AuthConstants.LOGIN_USER_SESSION_KEY);
        if (session != null) {
            session.invalidate();
        }
        if (loginUser != null) {
            log.info(
                    "event=user_logout userId={} username={}",
                    loginUser.id(),
                    sanitizeLogValue(loginUser.username())
            );
        }
        return new LogoutResponse("退出登录成功", Instant.now());
    }

    @Override
    @Transactional
    // 校验密码后注销当前账号。
    public LogoutResponse deleteAccount(DeleteAccountRequest request, HttpServletRequest httpRequest) {
        AuthUser authUser = requireActiveUser(httpRequest);
        if (!passwordEncoder.matches(request.password(), authUser.getPasswordHash())) {
            log.warn(
                    "event=user_delete_failed userId={} username={} reason=bad_credentials",
                    authUser.getId(),
                    sanitizeLogValue(authUser.getUsername())
            );
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "密码校验失败，无法注销当前账号");
        }

        authUser.setStatus(0);
        authUserMapper.updateById(authUser);

        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        log.info(
                "event=user_deleted userId={} username={}",
                authUser.getId(),
                sanitizeLogValue(authUser.getUsername())
        );
        return new LogoutResponse("账号已注销", Instant.now());
    }

    // 创建或修正启动时的种子管理员账号。
    public AuthUser createSeedUser(String username, String rawPassword, String displayName) {
        AuthUser authUser = new AuthUser();
        authUser.setUsername(normalizeUsername(username));
        authUser.setPasswordHash(passwordEncoder.encode(rawPassword));
        authUser.setDisplayName(displayName);
        authUser.setRole(ROLE_ADMIN);
        authUser.setStatus(1);
        try {
            authUserMapper.insert(authUser);
        } catch (DuplicateKeyException exception) {
            AuthUser existingSeedUser = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                    .eq(AuthUser::getUsername, normalizeUsername(username))
                    .eq(AuthUser::getStatus, 1)
                    .last("LIMIT 1"));
            if (existingSeedUser != null && !ROLE_ADMIN.equals(existingSeedUser.getRole())) {
                existingSeedUser.setRole(ROLE_ADMIN);
                authUserMapper.updateById(existingSeedUser);
            }
            return existingSeedUser;
        }
        return authUser;
    }

    @Override
    // 获取当前请求的有效用户。
    public AuthUser requireActiveUser(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        LoginUser loginUser = session == null
                ? null
                : (LoginUser) session.getAttribute(AuthConstants.LOGIN_USER_SESSION_KEY);
        if (loginUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或会话已过期，请重新登录");
        }

        AuthUser authUser = authUserMapper.selectById(loginUser.id());
        if (authUser == null || authUser.getStatus() == null || authUser.getStatus() != 1) {
            session.invalidate();
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "当前账号不可用，请重新登录");
        }
        return authUser;
    }

    // 按用户名查找唯一有效用户。
    private AuthUser findSingleActiveUserByUsername(String username) {
        List<AuthUser> authUsers = authUserMapper.selectList(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getUsername, username)
                .eq(AuthUser::getStatus, 1)
                .orderByDesc(AuthUser::getCreatedAt));

        if (authUsers.isEmpty()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (authUsers.size() > 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "该用户名存在多个有效账号，当前登录规则无法唯一定位用户");
        }
        return authUsers.get(0);
    }

    // 校验用户名未被有效账号占用。
    private void ensureUsernameAvailable(String username) {
        Long activeCount = authUserMapper.selectCount(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getUsername, username)
                .eq(AuthUser::getStatus, 1));
        if (activeCount != null && activeCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已被占用，请换一个");
        }
    }

    // 规范化用户名。
    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    // 清洗写入日志的用户输入。
    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 128 ? sanitized : sanitized.substring(0, 128);
    }

    // 转换为前端可见的用户视图。
    private AuthUserView toView(AuthUser authUser) {
        return new AuthUserView(
                authUser.getId(),
                authUser.getUsername(),
                authUser.getDisplayName(),
                authUser.getRole(),
                authUser.getStatus(),
                authUser.getLastLoginAt(),
                authUser.getCreatedAt()
        );
    }
}
