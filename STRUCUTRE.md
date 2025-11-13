src/main/java/
└─ com/yourorg/attendance/         <- root package (replace yourorg)
   ├─ model/
   │   ├─ Participant.java
   │   └─ TransRecord.java
   │
   ├─ db/
   │   ├─ dao/
   │   │   ├─ ParticipantsDao.java
   │   │   └─ TransDao.java
   │   ├─ AccessDb.java            <- thin DB factory + provisioning
   │   └─ migrations/              <- any SQL seeds / migration helpers
   │
   ├─ service/
   │   ├─ ImportService.java       <- import logic, uses dao + Excel loader
   │   ├─ AttendanceService.java   <- insertTrans / updateParticipantsRecord wrappers
   │   └─ NfcService.java          <- high-level NFC operations
   │
   ├─ nfc/
   │   ├─ SmartMifareReader.java
   │   ├─ SmartMifareWriter.java
   │   └─ SmartMifareEraser.java
   │
   ├─ ui/
   │   ├─ controller/
   │   │   ├─ DashboardController.java
   │   │   ├─ AttendanceController.java
   │   │   └─ ImportController.java
   │   ├─ view/
   │   │   ├─ AttendanceView.java
   │   │   └─ EntryFormView.java
   │   └─ dialog/
   │       ├─ ExcelImportDialog.java  <- lightweight UI only; delegates work to ImportService
   │       └─ BatchFilterDialog.java
   │
   ├─ excel/
   │   ├─ ExcelLoader.java        <- pure POI read logic
   │   └─ ExcelRowMapper.java     <- map sheet rows -> model/map
   │
   ├─ util/
   │   ├─ PhoneNormalizer.java
   │   ├─ DateUtils.java
   │   └─ FxUtils.java
   │
   └─ App.java  <- application bootstrap (main)
