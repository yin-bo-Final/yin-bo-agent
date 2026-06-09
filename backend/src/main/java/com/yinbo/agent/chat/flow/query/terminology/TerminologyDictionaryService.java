package com.yinbo.agent.chat.flow.query.terminology;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.chat.entity.ChatTerminologyAlias;
import com.yinbo.agent.chat.entity.ChatTerminologyTerm;
import com.yinbo.agent.chat.flow.query.terminology.TerminologyDictionaryCacheService.TerminologyAliasEntry;
import com.yinbo.agent.chat.flow.query.terminology.TerminologyDictionaryCacheService.TerminologyDictionaryEntry;
import com.yinbo.agent.chat.mapper.ChatTerminologyAliasMapper;
import com.yinbo.agent.chat.mapper.ChatTerminologyTermMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
// 术语字典查询服务，使用 Redis 旁路缓存。
public class TerminologyDictionaryService {

    private final ChatTerminologyTermMapper termMapper;
    private final ChatTerminologyAliasMapper aliasMapper;
    private final TerminologyDictionaryCacheService cacheService;

    // 注入术语表 Mapper 和缓存服务。
    public TerminologyDictionaryService(
            ChatTerminologyTermMapper termMapper,
            ChatTerminologyAliasMapper aliasMapper,
            TerminologyDictionaryCacheService cacheService
    ) {
        this.termMapper = termMapper;
        this.aliasMapper = aliasMapper;
        this.cacheService = cacheService;
    }

    // 查询启用术语字典。
    public List<TerminologyDictionaryEntry> enabledDictionary() {
        List<TerminologyDictionaryEntry> cached = cacheService.get();
        if (cached != null) {
            return cached;
        }
        List<TerminologyDictionaryEntry> dictionary = loadEnabledDictionaryFromDatabase();
        cacheService.put(dictionary);
        return dictionary;
    }

    // 删除术语字典缓存。
    public void evictCache() {
        cacheService.evict();
    }

    private List<TerminologyDictionaryEntry> loadEnabledDictionaryFromDatabase() {
        List<ChatTerminologyTerm> terms = termMapper.selectList(new LambdaQueryWrapper<ChatTerminologyTerm>()
                .eq(ChatTerminologyTerm::getEnabled, true)
                .orderByDesc(ChatTerminologyTerm::getPriority)
                .orderByDesc(ChatTerminologyTerm::getId));
        if (terms.isEmpty()) {
            return List.of();
        }

        List<Long> termIds = terms.stream().map(ChatTerminologyTerm::getId).toList();
        Map<Long, List<ChatTerminologyAlias>> aliasMap = aliasMapper.selectList(
                        new LambdaQueryWrapper<ChatTerminologyAlias>()
                                .in(ChatTerminologyAlias::getTermId, termIds)
                                .eq(ChatTerminologyAlias::getEnabled, true)
                                .orderByDesc(ChatTerminologyAlias::getPriority)
                                .orderByDesc(ChatTerminologyAlias::getId)
                )
                .stream()
                .collect(Collectors.groupingBy(ChatTerminologyAlias::getTermId));

        return terms.stream()
                .map(term -> toEntry(term, aliasMap.getOrDefault(term.getId(), List.of())))
                .filter(entry -> !entry.aliases().isEmpty())
                .toList();
    }

    private TerminologyDictionaryEntry toEntry(ChatTerminologyTerm term, List<ChatTerminologyAlias> aliases) {
        return new TerminologyDictionaryEntry(
                term.getId(),
                term.getCanonicalName(),
                term.getTermType(),
                term.getPriority(),
                aliases.stream()
                        .map(alias -> new TerminologyAliasEntry(
                                alias.getId(),
                                alias.getAliasName(),
                                normalizeAlias(alias.getAliasName()),
                                alias.getPriority()
                        ))
                        .toList()
        );
    }

    // 规范化别名，用于后台保存和匹配。
    public static String normalizeAlias(String alias) {
        return alias == null ? "" : alias.trim().toLowerCase();
    }
}
