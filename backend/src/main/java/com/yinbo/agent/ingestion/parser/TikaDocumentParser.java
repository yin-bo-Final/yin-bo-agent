package com.yinbo.agent.ingestion.parser;

import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.ingestion.ParsedDocument;
import com.yinbo.agent.ingestion.RawDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import com.yinbo.agent.storage.ObjectStorageService;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class TikaDocumentParser {

    private static final String PARSER_NAME = "TIKA";

    private final AutoDetectParser parser = new AutoDetectParser();
    private final ObjectStorageService objectStorageService;

    public TikaDocumentParser(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    public ParsedDocument parse(RawDocument rawDocument) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, rawDocument.fileName());
        if (rawDocument.contentType() != null && !rawDocument.contentType().isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, rawDocument.contentType());
        }

        BodyContentHandler handler = new BodyContentHandler(-1);
        ParseContext context = new ParseContext();
        try (InputStream inputStream = openInputStream(rawDocument)) {
            parser.parse(inputStream, handler, metadata, context);
        } catch (BusinessException exception) {
            throw exception;
        } catch (SAXException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文档解析失败，文件内容可能不完整或格式不受支持");
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "原始文件读取失败，请确认文件仍存在于 RustFS");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文档解析失败，请检查文件格式");
        }

        String text = handler.toString();
        if (text == null || text.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "没有从文档中解析出可用文本");
        }
        return new ParsedDocument(text, resolveTitle(metadata, rawDocument.fileName()), toMetadataMap(metadata));
    }

    public String parserName() {
        return PARSER_NAME;
    }

    private InputStream openInputStream(RawDocument rawDocument) throws IOException {
        if (rawDocument.hasStoredObject()) {
            return objectStorageService.open(rawDocument.storageBucket(), rawDocument.storageObjectKey());
        }
        if (rawDocument.bytes() != null) {
            return new ByteArrayInputStream(rawDocument.bytes());
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "原始文件不存在，请重新上传");
    }

    private String resolveTitle(Metadata metadata, String fileName) {
        String title = metadata.get(TikaCoreProperties.TITLE);
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private Map<String, String> toMetadataMap(Metadata metadata) {
        Map<String, String> metadataMap = new LinkedHashMap<>();
        for (String name : metadata.names()) {
            String value = metadata.get(name);
            if (value != null && !value.isBlank()) {
                metadataMap.put(name, value);
            }
        }
        return metadataMap;
    }
}
