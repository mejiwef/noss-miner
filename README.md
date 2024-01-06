# NOSS-MINER-JAVA
## 运行环境
JDK11, Maven 3.6.3

## 修改配置
复制 src/main/resources/miner.setting.example 到 src/main/resources/miner.setting
修改miner.setting中的配置

## 构建
```bash
## 项目根目录下执行
mvn clean package
## 进入target目录执行
java -jar noss-miner-1.0-SNAPSHOT-jar-with-dependencies.jar
```