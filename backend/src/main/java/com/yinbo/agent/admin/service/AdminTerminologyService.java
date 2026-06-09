package com.yinbo.agent.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.admin.dto.TerminologyMappingRequest;
import com.yinbo.agent.admin.dto.TerminologyMappingResponse;
import com.yinbo.agent.chat.entity.ChatTerminologyAlias;
import com.yinbo.agent.chat.entity.ChatTerminologyTerm;
import com.yinbo.agent.chat.flow.query.terminology.TerminologyDictionaryService;
import com.yinbo.agent.chat.mapper.ChatTerminologyAliasMapper;
import com.yinbo.agent.chat.mapper.ChatTerminologyTermMapper;
import com.yinbo.agent.common.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
// 管理后台术语映射维护服务。
public class AdminTerminologyService {

    private final ChatTerminologyTermMapper termMapper;
    private final ChatTerminologyAliasMapper aliasMapper;
    private final TerminologyDictionaryService dictionaryService;

    // 注入术语 Mapper 和术语字典服务。
    public AdminTerminologyService(
            ChatTerminologyTermMapper termMapper,
            ChatTerminologyAliasMapper aliasMapper,
            TerminologyDictionaryService dictionaryService
    ) {
        this.termMapper = termMapper;
        this.aliasMapper = aliasMapper;
        this.dictionaryService = dictionaryService;
    }

    // 查询关键词映射列表。
    public List<TerminologyMappingResponse> listMappings() {
        List<ChatTerminologyTerm> terms = termMapper.selectList(new LambdaQueryWrapper<ChatTerminologyTerm>()
                .orderByDesc(ChatTerminologyTerm::getPriority)
                .orderByDesc(ChatTerminologyTerm::getId));
        Map<Long, ChatTerminologyTerm> termMap = terms.stream()
                .collect(Collectors.toMap(ChatTerminologyTerm::getId, Function.identity()));
        return aliasMapper.selectList(new LambdaQueryWrapper<ChatTerminologyAlias>()
                        .orderByDesc(ChatTerminologyAlias::getUpdatedAt)
                        .orderByDesc(ChatTerminologyAlias::getId))
                .stream()
                .map(alias -> toResponse(alias, termMap.get(alias.getTermId())))
                .filter(response -> response.termId() != null)
                .toList();
    }

    @Transactional
    // 新增关键词映射。
    public TerminologyMappingResponse createMapping(TerminologyMappingRequest request) {
        ChatTerminologyTerm term = findOrCreateTerm(request);
        ensureAliasAvailable(request.aliasName(), null);
        ChatTerminologyAlias alias = new ChatTerminologyAlias();
        alias.setTermId(term.getId());
        alias.setAliasName(normalizeText(request.aliasName()));
        alias.setAliasNormalized(TerminologyDictionaryService.normalizeAlias(request.aliasName()));
        alias.setPriority(priorityOrDefault(request.priority()));
        alias.setEnabled(request.enabled() == null || request.enabled());
        aliasMapper.insert(alias);
        runAfterCommit(dictionaryService::evictCache);
        return toResponse(alias, term);
    }

    @Transactional
    // 修改关键词映射。
    public TerminologyMappingResponse updateMapping(Long aliasId, TerminologyMappingRequest request) {
        ChatTerminologyAlias alias = requireAlias(aliasId);
        requireTerm(alias.getTermId());
        ensureAliasAvailable(request.aliasName(), aliasId);

        ChatTerminologyTerm term = findOrCreateTerm(request);
        term.setTermType(termTypeOrDefault(request.termType()));
        term.setDescription(blankToNull(request.description()));
        term.setPriority(priorityOrDefault(request.priority()));
        term.setEnabled(true);
        termMapper.updateById(term);

        alias.setTermId(term.getId());
        alias.setAliasName(normalizeText(request.aliasName()));
        alias.setAliasNormalized(TerminologyDictionaryService.normalizeAlias(request.aliasName()));
        alias.setPriority(priorityOrDefault(request.priority()));
        alias.setEnabled(request.enabled() == null || request.enabled());
        aliasMapper.updateById(alias);
        runAfterCommit(dictionaryService::evictCache);
        return toResponse(alias, term);
    }

    @Transactional
    // 启用或禁用关键词映射。
    public TerminologyMappingResponse updateMappingEnabled(Long aliasId, boolean enabled) {
        ChatTerminologyAlias alias = requireAlias(aliasId);
        ChatTerminologyTerm term = requireTerm(alias.getTermId());
        alias.setEnabled(enabled);
        aliasMapper.updateById(alias);
        runAfterCommit(dictionaryService::evictCache);
        return toResponse(alias, term);
    }

    @Transactional
    // 删除关键词映射别名。
    public void deleteMapping(Long aliasId) {
        requireAlias(aliasId);
        aliasMapper.deleteById(aliasId);
        runAfterCommit(dictionaryService::evictCache);
    }

    private ChatTerminologyTerm findOrCreateTerm(TerminologyMappingRequest request) {
        String canonicalName = normalizeText(request.canonicalName());
        ChatTerminologyTerm term = termMapper.selectOne(new LambdaQueryWrapper<ChatTerminologyTerm>()
                .eq(ChatTerminologyTerm::getCanonicalName, canonicalName)
                .last("LIMIT 1"));
        if (term != null) {
            if (!Boolean.TRUE.equals(term.getEnabled())) {
                term.setEnabled(true);
                termMapper.updateById(term);
            }
            return term;
        }
        ChatTerminologyTerm nextTerm = new ChatTerminologyTerm();
        nextTerm.setCanonicalName(canonicalName);
        nextTerm.setTermType(termTypeOrDefault(request.termType()));
        nextTerm.setDescription(blankToNull(request.description()));
        nextTerm.setPriority(priorityOrDefault(request.priority()));
        nextTerm.setEnabled(true);
        termMapper.insert(nextTerm);
        return nextTerm;
    }

    private void ensureAliasAvailable(String aliasName, Long currentAliasId) {
        String normalized = TerminologyDictionaryService.normalizeAlias(aliasName);
        ChatTerminologyAlias existing = aliasMapper.selectOne(new LambdaQueryWrapper<ChatTerminologyAlias>()
                .eq(ChatTerminologyAlias::getAliasNormalized, normalized)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(currentAliasId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "这个原始词已经存在映射");
        }
    }

    private ChatTerminologyAlias requireAlias(Long aliasId) {
        ChatTerminologyAlias alias = aliasMapper.selectById(aliasId);
        if (alias == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "关键词映射不存在");
        }
        return alias;
    }

    private ChatTerminologyTerm requireTerm(Long termId) {
        ChatTerminologyTerm term = termMapper.selectById(termId);
        if (term == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "目标术语不存在");
        }
        return term;
    }

    private TerminologyMappingResponse toResponse(ChatTerminologyAlias alias, ChatTerminologyTerm term) {
        if (term == null) {
            return new TerminologyMappingResponse(null, idToString(alias.getId()), alias.getAliasName(), "-", "-", null, 0, false, alias.getCreatedAt(), alias.getUpdatedAt());
        }
        boolean enabled = Boolean.TRUE.equals(alias.getEnabled()) && Boolean.TRUE.equals(term.getEnabled());
        return new TerminologyMappingResponse(
                idToString(term.getId()),
                idToString(alias.getId()),
                alias.getAliasName(),
                term.getCanonicalName(),
                term.getTermType(),
                term.getDescription(),
                alias.getPriority(),
                enabled,
                alias.getCreatedAt(),
                alias.getUpdatedAt()
        );
    }

    private String idToString(Long value) {
        return value == null ? null : value.toString();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String termTypeOrDefault(String value) {
        return value == null || value.isBlank() ? "TECH" : value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int priorityOrDefault(Integer value) {
        return value == null ? 0 : value;
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            // 事务提交后删除术语缓存。
            public void afterCommit() {
                action.run();
            }
        });
    }
}
