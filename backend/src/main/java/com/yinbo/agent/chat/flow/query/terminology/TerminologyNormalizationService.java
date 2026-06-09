package com.yinbo.agent.chat.flow.query.terminology;

import com.yinbo.agent.chat.flow.query.terminology.TerminologyDictionaryCacheService.TerminologyAliasEntry;
import com.yinbo.agent.chat.flow.query.terminology.TerminologyDictionaryCacheService.TerminologyDictionaryEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
// 查询术语统一服务。
public class TerminologyNormalizationService {

    private final TerminologyDictionaryService dictionaryService;

    // 注入术语字典服务。
    public TerminologyNormalizationService(TerminologyDictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    // 对本轮用户问题做术语统一。
    public TerminologyNormalizationResult normalize(String query) {
        String originalQuery = query == null ? "" : query;
        if (originalQuery.isBlank()) {
            return new TerminologyNormalizationResult(originalQuery, originalQuery, List.of());
        }

        List<AliasCandidate> candidates = flattenCandidates(dictionaryService.enabledDictionary());
        if (candidates.isEmpty()) {
            return new TerminologyNormalizationResult(originalQuery, originalQuery, List.of());
        }

        List<TerminologyMatch> matches = findMatches(originalQuery, candidates);
        if (matches.isEmpty()) {
            return new TerminologyNormalizationResult(originalQuery, originalQuery, List.of());
        }
        return new TerminologyNormalizationResult(originalQuery, replaceMatches(originalQuery, matches), matches);
    }

    private List<AliasCandidate> flattenCandidates(List<TerminologyDictionaryEntry> dictionary) {
        List<AliasCandidate> candidates = new ArrayList<>();
        for (TerminologyDictionaryEntry term : dictionary) {
            for (TerminologyAliasEntry alias : term.aliases()) {
                if (alias.aliasName() == null || alias.aliasName().isBlank()) {
                    continue;
                }
                candidates.add(new AliasCandidate(term, alias));
            }
        }
        candidates.sort(Comparator
                .comparingInt(AliasCandidate::aliasLength).reversed()
                .thenComparing(AliasCandidate::termPriority, Comparator.reverseOrder())
                .thenComparing(AliasCandidate::aliasPriority, Comparator.reverseOrder()));
        return candidates;
    }

    private List<TerminologyMatch> findMatches(String query, List<AliasCandidate> candidates) {
        String lowerQuery = query.toLowerCase();
        boolean[] occupied = new boolean[query.length()];
        List<TerminologyMatch> matches = new ArrayList<>();
        for (AliasCandidate candidate : candidates) {
            String alias = candidate.alias().aliasName();
            String normalizedAlias = alias.toLowerCase();
            int fromIndex = 0;
            while (fromIndex < lowerQuery.length()) {
                int start = lowerQuery.indexOf(normalizedAlias, fromIndex);
                if (start < 0) {
                    break;
                }
                int end = start + normalizedAlias.length();
                if (isValidMatch(query, start, end, alias) && isFree(occupied, start, end)) {
                    markOccupied(occupied, start, end);
                    matches.add(toMatch(candidate, query.substring(start, end), start, end));
                }
                fromIndex = Math.max(end, start + 1);
            }
        }
        matches.sort(Comparator.comparingInt(TerminologyMatch::start));
        return List.copyOf(matches);
    }

    private TerminologyMatch toMatch(AliasCandidate candidate, String raw, int start, int end) {
        TerminologyDictionaryEntry term = candidate.term();
        TerminologyAliasEntry alias = candidate.alias();
        return new TerminologyMatch(
                term.termId(),
                alias.aliasId(),
                raw,
                term.canonicalName(),
                term.termType(),
                start,
                end
        );
    }

    private boolean isValidMatch(String query, int start, int end, String alias) {
        if (!isAsciiWord(alias)) {
            return true;
        }
        boolean leftBoundary = start == 0 || !isAsciiWordChar(query.charAt(start - 1));
        boolean rightBoundary = end >= query.length() || !isAsciiWordChar(query.charAt(end));
        return leftBoundary && rightBoundary;
    }

    private boolean isAsciiWord(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!isAsciiWordChar(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean isAsciiWordChar(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9')
                || value == '_'
                || value == '-';
    }

    private boolean isFree(boolean[] occupied, int start, int end) {
        for (int index = start; index < end && index < occupied.length; index++) {
            if (occupied[index]) {
                return false;
            }
        }
        return true;
    }

    private void markOccupied(boolean[] occupied, int start, int end) {
        for (int index = start; index < end && index < occupied.length; index++) {
            occupied[index] = true;
        }
    }

    private String replaceMatches(String query, List<TerminologyMatch> matches) {
        StringBuilder builder = new StringBuilder(query);
        for (int index = matches.size() - 1; index >= 0; index--) {
            TerminologyMatch match = matches.get(index);
            if (match.canonical() == null || match.canonical().isBlank()) {
                continue;
            }
            builder.replace(match.start(), match.end(), match.canonical());
        }
        return builder.toString();
    }

    private record AliasCandidate(TerminologyDictionaryEntry term, TerminologyAliasEntry alias) {

        private int aliasLength() {
            return alias.aliasName() == null ? 0 : alias.aliasName().length();
        }

        private Integer termPriority() {
            return term.priority() == null ? 0 : term.priority();
        }

        private Integer aliasPriority() {
            return alias.priority() == null ? 0 : alias.priority();
        }
    }
}
