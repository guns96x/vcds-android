# VCDS Mobile for Android — VAG KWP2000 Diagnostics & WOT Logger

Мобільний діагностичний інструмент та логер для блоків керування двигуном Bosch EDC16 (Volkswagen Golf 5 1.9 TDI BLS та інші платформи VAG PQ35).

---

## 🚀 Основні можливості

1. **Пряме підключення через USB-OTG**:
   - Підтримка адаптерів FTDI (FT232R/BM — використовується в оригінальних та клонах VCDS / HEX-CAN / KKL 409.1).
   - Підтримка чіпів CH340, CP2102, Prolific.
   - Протокол KWP2000 (ISO 14230-2) по K-Line на швидкості 10400 bps з Fast Init (25 ms Break / Mark).

2. **Вимірювальні групи в реальному часі (Measuring Blocks)**:
   - **Group 011 (Charge Pressure Control)**:
     - Оберти двигуна (RPM)
     - Заданий тиск наддуву (Specified Boost, mbar)
     - Фактичний тиск наддуву (Actual Boost, mbar)
     - Скважність клапана N75 (N75 Duty Cycle, %)
   - **Group 008 (Injected Quantity Limitations)**:
     - Оберти двигуна (RPM)
     - Запит водія (Driver's Wish IQ, mg/str)
     - Обмеження по моменту (Torque Limit IQ, mg/str)
     - Димове обмеження (Smoke Limit IQ, mg/str)
   - **Group 003 (EGR & MAF)**:
     - Оберти двигуна (RPM)
     - Заданий потік повітря (MAF Specified, mg/str)
     - Фактичний потік повітря (MAF Actual, mg/str)
     - Скважність EGR (%)

3. **Вбудований осцилограф (Live Scope Graph)**:
   - Апаратне відмалювання графіків на полотні Canvas (60 fps).
   - Синхронне відображення кривих: Target Boost (Cyan), Actual Boost (Green), N75 % (Orange).

4. **Високошвидкісний WOT CSV Logger**:
   - Однокнопковий запис розгону на 4-й передачі (1400–4000 RPM) з частотою до 15-20 Гц.
   - Збереження у `/sdcard/Android/data/com.vag.vcdsandroid/files/Documents/VCDS_Logs/`.
   - Автоматичний розрахунок пікового наддуву, часу лагу (spool lag) та кількості точок.

5. **Зчитування та очищення кодів несправностей (DTC)**:
   - KWP2000 Service 0x18 (Read DTC) та Service 0x14 (Clear DTC).
   - Вбудована база несправностей VAG з перекладом українською та англійською мовами.

6. **Режим симуляції (Offline Demo Mode)**:
   - Можливість тестувати інтерфейс, графіки та запис CSV без підключення до авто.
   - Симулює реалістичний розгін на 4-й передачі з лагом виходу на буст турбіни BV39 на двигуні BLS.
