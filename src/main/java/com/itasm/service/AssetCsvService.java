package com.itasm.service;

import com.itasm.entity.Asset;
import com.itasm.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetCsvService {

    private final AssetRepository assetRepository;
    
    public byte[] exportAssetsToCsv() throws Exception {
        return exportAssetsToCsv(null);
    }

    public byte[] exportAssetsToCsv(List<Long> assetIds) throws Exception {
        List<Asset> assets;
        if (assetIds != null && !assetIds.isEmpty()) {
            assets = assetRepository.findAllById(assetIds);
        } else {
            assets = assetRepository.findAll();
        }
        
        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setHeader("ID", "Tag", "Serial", "Type", "Brand", "Model", "Status").build())) {
            for (Asset a : assets) {
                printer.printRecord(
                        a.getId(),
                        a.getAssetTag(),
                        a.getSerialNumber(),
                        a.getType(),
                        a.getBrand(),
                        a.getModel(),
                        a.getStatus()
                );
            }
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    public int importAssetsFromCsv(MultipartFile file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
             
            List<Asset> assetsToSave = new ArrayList<>();
            for (CSVRecord record : csvParser) {
                Asset asset = Asset.builder()
                        .assetTag(record.get("Tag"))
                        .serialNumber(record.get("Serial"))
                        .type(record.get("Type"))
                        .brand(record.get("Brand"))
                        .model(record.get("Model"))
                        .build();
                assetsToSave.add(asset);
            }
            assetRepository.saveAll(assetsToSave);
            return assetsToSave.size();
        }
    }
}
