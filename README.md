# BoxQuest

Plugin cho server BoxPvP (Paper 1.21.1, Java 21).

## Build

```
./gradlew build
```

Repo chỉ có mã nguồn, không chứa file jar nào (trừ `gradle/wrapper/gradle-wrapper.jar` chuẩn của Gradle).
Trước khi build, tự đặt các jar sau vào thư mục `libs/` ở gốc repo:

- `boxcore-api-1.2.0.jar` - build từ [BoxCore](https://github.com/FLD-TN/BoxCore) (`boxcore-api/build/libs/`)
