package com.yinbo.agent.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

@Service
public class SessionAuthService implements AuthService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

    private final AuthUserMapper authUserMapper;
    private final PasswordEncoder passwordEncoder;

    public SessionAuthService(AuthUserMapper authUserMapper, PasswordEncoder passwordEncoder) {
        this.authUserMapper = authUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
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
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已被占用，请换一个");
        }

        return createLoginSession(authUser, httpRequest);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        AuthUser authUser = findSingleActiveUserByUsername(normalizeUsername(request.username()));

        if (!passwordEncoder.matches(request.password(), authUser.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        return createLoginSession(authUser, httpRequest);
    }

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

        return new LoginResponse(session.getId(), loginUser.loginAt(), toView(authUser));
    }

    @Override
    public CurrentUserResponse currentUser(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        LoginUser loginUser = session == null
                ? null
                : (LoginUser) session.getAttribute(AuthConstants.LOGIN_USER_SESSION_KEY);
        AuthUser authUser = requireActiveUser(httpRequest);
        return new CurrentUserResponse(session.getId(), loginUser.loginAt(), toView(authUser));
    }

    @Override
    public LogoutResponse logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return new LogoutResponse("退出登录成功", Instant.now());
    }

    @Override
    @Transactional
    public LogoutResponse deleteAccount(DeleteAccountRequest request, HttpServletRequest httpRequest) {
        AuthUser authUser = requireActiveUser(httpRequest);
        if (!passwordEncoder.matches(request.password(), authUser.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "密码校验失败，无法注销当前账号");
        }

        authUser.setStatus(0);
        authUserMapper.updateById(authUser);

        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return new LogoutResponse("账号已注销", Instant.now());
    }

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

    private void ensureUsernameAvailable(String username) {
        Long activeCount = authUserMapper.selectCount(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getUsername, username)
                .eq(AuthUser::getStatus, 1));
        if (activeCount != null && activeCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已被占用，请换一个");
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

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
