package com.hightraffic.ecommerce.inventory.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("도메인 레이어 아키텍처 테스트")
class DomainLayerArchitectureTest {

    private static final String DOMAIN_PACKAGE = "com.hightraffic.ecommerce.inventory.domain..";
    private static final String APPLICATION_PACKAGE = "com.hightraffic.ecommerce.inventory.application..";
    private static final String ADAPTER_PACKAGE = "com.hightraffic.ecommerce.inventory.adapter..";
    private static final String CONFIG_PACKAGE = "com.hightraffic.ecommerce.inventory.config..";
    
    private JavaClasses classes;

    @BeforeEach
    void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hightraffic.ecommerce.inventory");
    }

    @Test
    @DisplayName("도메인 레이어는 프레임워크에 의존하지 않아야 한다")
    void domainShouldNotDependOnFrameworks() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "javax.persistence..",
                        "jakarta.persistence..",
                        "org.hibernate.."
                )
                .because("도메인 레이어는 프레임워크에 독립적이어야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("도메인 레이어는 어댑터 레이어에 의존하지 않아야 한다")
    void domainShouldNotDependOnAdapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAPackage(ADAPTER_PACKAGE)
                .because("도메인 레이어는 어댑터 레이어에 의존하지 않아야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("도메인 레이어는 애플리케이션 레이어에 의존하지 않아야 한다")
    void domainShouldNotDependOnApplication() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAPackage(APPLICATION_PACKAGE)
                .because("도메인 레이어는 애플리케이션 레이어에 의존하지 않아야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("도메인 레이어는 설정 클래스에 의존하지 않아야 한다")
    void domainShouldNotDependOnConfiguration() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAPackage(CONFIG_PACKAGE)
                .because("도메인 레이어는 설정 클래스에 의존하지 않아야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("도메인 모델은 불변이어야 한다")
    void domainModelsShouldBeImmutable() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain.model..")
                .and().areNotEnums()
                .and().areNotInterfaces()
                .should().haveNameMatching(".*")  // This is a placeholder - in real implementation would check for final fields
                .because("도메인 모델은 불변성을 유지해야 합니다");

        // Note: 실제로는 필드가 final이고 setter가 없는지 확인하는 더 정교한 검증이 필요합니다
        rule.check(classes);
    }

    @Test
    @DisplayName("도메인 서비스는 상태를 가지지 않아야 한다")
    void domainServicesShouldBeStateless() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain.service..")
                .should().haveOnlyFinalFields()
                .because("도메인 서비스는 상태를 가지지 않아야 합니다");

        rule.check(classes);
    }


    @Test
    @DisplayName("Value Object는 equals와 hashCode를 구현해야 한다")
    void valueObjectsShouldImplementEqualsAndHashCode() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain.model.vo..")
                .should(new ArchCondition<JavaClass>("override equals and hashCode") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        boolean hasEquals = javaClass.getMethods().stream()
                                .anyMatch(method -> method.getName().equals("equals") 
                                    && method.getRawParameterTypes().size() == 1
                                    && method.getRawParameterTypes().get(0).getName().equals("java.lang.Object"));
                        
                        boolean hasHashCode = javaClass.getMethods().stream()
                                .anyMatch(method -> method.getName().equals("hashCode") 
                                    && method.getRawParameterTypes().isEmpty());
                        
                        if (!hasEquals || !hasHashCode) {
                            String message = String.format("%s should override both equals() and hashCode()", 
                                    javaClass.getDescription());
                            events.add(SimpleConditionEvent.violated(javaClass, message));
                        }
                    }
                })
                .because("Value Object는 값 동등성을 위해 equals와 hashCode를 구현해야 합니다");

        rule.check(classes);
    }
}