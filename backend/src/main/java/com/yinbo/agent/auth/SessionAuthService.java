package com.yinbo.agent.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.auth.dto.AuthUserView;
import com.yinbo.agent.auth.dto.CurrentUserResponse;
import com.yinbo.agent.auth.dto.LoginRequest;
import com.yinbo.agent.auth.dto.LoginResponse;
import com.yinbo.agent.auth.dto.LogoutResponse;
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

@Service
public class SessionAuthService implements AuthService {

    private final AuthUserMapper authUserMapper;
    private final PasswordEncoder passwordEncoder;

    public SessionAuthService(AuthUserMapper authUserMapper, PasswordEncoder passwordEncoder) {
        this.authUserMapper = authUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {

        AuthUser authUser = findSingleActiveUserByUsername(request.username());

        if (!passwordEncoder.matches(request.password(), authUser.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

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

    public AuthUser createSeedUser(String username, String rawPassword, String displayName) {
        AuthUser authUser = new AuthUser();
        authUser.setUsername(username);
        authUser.setPasswordHash(passwordEncoder.encode(rawPassword));
        authUser.setDisplayName(displayName);
        authUser.setStatus(1);
        try {
            authUserMapper.insert(authUser);
        } catch (DuplicateKeyException exception) {
            return authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                    .eq(AuthUser::getUsername, username)
                    .last("LIMIT 1"));
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

    private AuthUserView toView(AuthUser authUser) {
        return new AuthUserView(
                authUser.getId(),
                authUser.getUsername(),
                authUser.getDisplayName(),
                authUser.getStatus(),
                authUser.getLastLoginAt(),
                authUser.getCreatedAt()
        );
    }
}
