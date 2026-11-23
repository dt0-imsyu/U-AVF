// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.diagnostics;

import java.util.List;

/** In report order. Saved results, export and the compatibility screen use these indices. */
public final class CompatibilityChecks {
    public static final int AVF_PERMISSIONS = 2;
    public static final List<String> LABELS = List.of(
            "Android / ARM64", "Платформа Android AVF", "Разрешения Custom VM",
            "Целостность платформы", "Создание VM", "Загрузка тестового ядра", "Память гостя",
            "Запись на тестовый диск", "Обмен через vsock");

    private CompatibilityChecks() {}
}
