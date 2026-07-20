package com.studytracker.config;

import com.studytracker.model.XpLevelConfig;
import com.studytracker.repository.XpLevelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final XpLevelConfigRepository xpLevelConfigRepository;

    @Override
    public void run(String... args) throws Exception {
        if (xpLevelConfigRepository.count() == 0) {
            List<XpLevelConfig> configs = new ArrayList<>();
            // Cấu hình từ level 1 đến 100
            for (int level = 1; level <= 100; level++) {
                // Công thức: xp_required(level) = 100 * level^1.5 (làm tròn số nguyên)
                int xpRequired = (int) Math.round(100 * Math.pow(level, 1.5));
                configs.add(XpLevelConfig.builder()
                        .level(level)
                        .xpRequired(xpRequired)
                        .build());
            }
            xpLevelConfigRepository.saveAll(configs);
            System.out.println("Đã khởi tạo seed data cho bảng XpLevelConfig (100 levels).");
        }
    }
}
