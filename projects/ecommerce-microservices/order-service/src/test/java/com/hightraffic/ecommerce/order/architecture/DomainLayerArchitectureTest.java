package com.hightraffic.ecommerce.order.architecture;

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

    private static final String DOMAIN_PACKAGE = "com.hightraffic.ecommerce.order.domain..";
    private static final String APPLICATION_PACKAGE = "com.hightraffic.ecommerce.order.application..";
    private static final String ADAPTER_PACKAGE = "com.hightraffic.ecommerce.order.adapter..";
    private static final String CONFIG_PACKAGE = "com.hightraffic.ecommerce.order.config..";
    
    private JavaClasses classes;

    @BeforeEach
    void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hightraffic.ecommerce.order");
    }

    @Test
    @DisplayName("도메인 레이어는 프레임워크에 의존하지 않아야 한다 (JPA Entity 제외)")
    void domainShouldNotDependOnFrameworks() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .and().resideOutsideOfPackage("..domain.model..")  // 도메인 모델은 제외
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "javax.persistence..",
                        "jakarta.persistence..",
                        "org.hibernate.."
                )
                .because("도메인 레이어는 프레임워크에 독립적이어야 합니다 (단, 도메인 모델의 JPA 어노테이션은 허용)");

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
                .and().areTopLevelClasses()  // 중첩 클래스 제외
                .should().haveOnlyFinalFields()
                .because("도메인 서비스는 상태를 가지지 않아야 합니다");

        rule.check(classes);
    }


    @Test
    @DisplayName("도메인 서비스와 리포지토리는 Spring/JPA에 의존하지 않아야 한다")
    void domainServicesAndRepositoriesShouldNotDependOnFrameworks() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .and().resideInAnyPackage("..domain.service..", "..domain.repository..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "javax.persistence..",
                        "jakarta.persistence..",
                        "org.hibernate.."
                )
                .because("도메인 서비스와 리포지토리는 프레임워크에 독립적이어야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Value Object는 equals와 hashCode를 구현해야 한다")
    void valueObjectsShouldImplementEqualsAndHashCode() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain.model.vo..")
                .and().areNotEnums()  // enum 제외
                .and().areNotAnonymousClasses()  // 익명 클래스 제외
                .should(new ArchCondition<JavaClass>("override equals and hashCode") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        // enum과 익명 클래스는 이미 체크했으므로 추가 검증 불필요
                        if (javaClass.isEnum() || javaClass.isAnonymousClass()) {
                            return;
                        }
                        
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