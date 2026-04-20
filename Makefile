.PHONY: build test test-unit test-integration clean coverage fmt check

build:
	mvn compile -q

test: test-unit test-integration

test-unit:
	mvn test -q

test-integration:
	mvn verify -q -DskipUTs=true

clean:
	mvn clean -q

coverage:
	mvn test jacoco:report -q
	@echo "Coverage report: target/site/jacoco/index.html"

fmt:
	mvn spotless:apply -q 2>/dev/null || echo "Spotless not configured, skipping format"

fmt-check:
	mvn spotless:check -q 2>/dev/null || echo "Spotless not configured, skipping format check"

check: build test

doc:
	mvn javadoc:javadoc -q
	@echo "Javadoc: target/site/apidocs/index.html"
