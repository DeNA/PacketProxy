
JAVA_PATH := /opt/homebrew/Cellar/openjdk@21/21.0.8/libexec/openjdk.jdk/Contents/Home

.PHONY: build

	
build:
	./gradlew assemble

rebuild:
	./gradlew clean
	./gradlew assemble

clean:
	./gradlew clean

run:
	RESTORE_HISTORY=y ./gradlew run 

run-clean:
	RESTORE_HISTORY=n ./gradlew run 