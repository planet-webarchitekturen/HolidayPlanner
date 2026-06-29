package com.holidayplanner.identityservice.command;

import com.holidayplanner.identityservice.client.BookingServiceClient;
import com.holidayplanner.identityservice.exception.ActiveBookingVetoException;
import com.holidayplanner.identityservice.kafka.IdentityEventProducer;
import com.holidayplanner.identityservice.repository.CaregiverRepository;
import com.holidayplanner.identityservice.repository.FamilyMemberRepository;
import com.holidayplanner.identityservice.repository.UserRepository;
import com.holidayplanner.shared.kafka.payload.FamilyMemberAddedPayload;
import com.holidayplanner.shared.kafka.payload.FamilyMemberRemovedPayload;
import com.holidayplanner.shared.kafka.payload.UserDeletedPayload;
import com.holidayplanner.shared.kafka.payload.UserRegisteredPayload;
import com.holidayplanner.shared.kafka.payload.UserUpdatedPayload;
import com.holidayplanner.shared.model.Caregiver;
import com.holidayplanner.shared.model.FamilyMember;
import com.holidayplanner.shared.model.User;
import com.holidayplanner.shared.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link IdentityCommandService} domain service.
 *
 * All collaborators (repositories, password encoder, event producer, booking
 * client) are mocked, so these tests exercise the service's business logic in
 * isolation — no Spring context, no database, no Kafka.
 */
@ExtendWith(MockitoExtension.class)
class IdentityCommandServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private CaregiverRepository caregiverRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IdentityEventProducer eventProducer;
    @Mock private BookingServiceClient bookingServiceClient;

    @InjectMocks private IdentityCommandService service;

    private UUID userId;
    private User existingUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        existingUser = new User();
        existingUser.setId(userId);
        existingUser.setEmail("alice@example.com");
        existingUser.setPasswordHash("oldHash");
        existingUser.setPhoneNumber("111");
        existingUser.setOrganizationId(UUID.randomUUID());
        existingUser.setRole(UserRole.USER);
    }

    @Nested
    class RegisterUser {

        @Test
        void registersAndPublishesEvent() {
            when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
            when(passwordEncoder.encode("secret")).thenReturn("hashedSecret");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            UUID orgId = UUID.randomUUID();

            User result = service.registerUser("bob@example.com", "secret", "222", orgId);

            assertThat(result.getEmail()).isEqualTo("bob@example.com");
            assertThat(result.getPasswordHash()).isEqualTo("hashedSecret");
            assertThat(result.getOrganizationId()).isEqualTo(orgId);

            ArgumentCaptor<UserRegisteredPayload> captor = ArgumentCaptor.forClass(UserRegisteredPayload.class);
            verify(eventProducer).publishUserRegistered(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("bob@example.com");
        }

        @Test
        void rejectsDuplicateEmail() {
            when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

            assertThatThrownBy(() -> service.registerUser("bob@example.com", "secret", "222", UUID.randomUUID()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("already exists");

            verify(userRepository, never()).save(any());
            verifyNoInteractions(eventProducer);
        }
    }

    @Nested
    class UpdateUser {

        @Test
        void partialUpdateChangesOnlyProvidedFields() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = service.updateUser(userId, null, "999", null, null, null);

            assertThat(result.getPhoneNumber()).isEqualTo("999");
            assertThat(result.getEmail()).isEqualTo("alice@example.com"); // unchanged
            assertThat(result.getPasswordHash()).isEqualTo("oldHash");     // unchanged

            ArgumentCaptor<UserUpdatedPayload> captor = ArgumentCaptor.forClass(UserUpdatedPayload.class);
            verify(eventProducer).publishUserUpdated(captor.capture());
            assertThat(captor.getValue().getPhoneNumber()).isEqualTo("999");
        }

        @Test
        void rehashesPasswordWhenProvided() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.encode("newPass")).thenReturn("newHash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = service.updateUser(userId, null, null, "newPass", null, null);

            assertThat(result.getPasswordHash()).isEqualTo("newHash");
        }

        @Test
        void rejectsEmailAlreadyTakenByAnotherUser() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

            assertThatThrownBy(() -> service.updateUser(userId, "taken@example.com", null, null, null, null))
                    .hasMessageContaining("already exists");

            verify(userRepository, never()).save(any());
            verifyNoInteractions(eventProducer);
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateUser(userId, null, "999", null, null, null))
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    class DeleteUser {

        @Test
        void deletesAndPublishesWhenNoActiveBookings() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            FamilyMember member = familyMemberOf(existingUser);
            when(familyMemberRepository.findByUser_Id(userId)).thenReturn(List.of(member));
            when(bookingServiceClient.getActiveBookingCount(member.getId())).thenReturn(0L);

            service.deleteUser(userId);

            verify(userRepository).delete(existingUser);
            ArgumentCaptor<UserDeletedPayload> captor = ArgumentCaptor.forClass(UserDeletedPayload.class);
            verify(eventProducer).publishUserDeleted(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        }

        @Test
        void vetoesWhenFamilyMemberHasActiveBookings() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            FamilyMember member = familyMemberOf(existingUser);
            when(familyMemberRepository.findByUser_Id(userId)).thenReturn(List.of(member));
            when(bookingServiceClient.getActiveBookingCount(member.getId())).thenReturn(1L);

            assertThatThrownBy(() -> service.deleteUser(userId))
                    .hasMessageContaining("active bookings");

            verify(userRepository, never()).delete(any());
            verifyNoInteractions(eventProducer);
        }
    }

    @Nested
    class FamilyMembers {

        @Test
        void addPublishesEvent() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(familyMemberRepository.save(any(FamilyMember.class))).thenAnswer(inv -> {
                FamilyMember m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            FamilyMember result = service.addFamilyMember(userId, "Kid", "Smith", LocalDate.of(2015, 1, 1), "12345");

            assertThat(result.getFirstName()).isEqualTo("Kid");
            ArgumentCaptor<FamilyMemberAddedPayload> captor = ArgumentCaptor.forClass(FamilyMemberAddedPayload.class);
            verify(eventProducer).publishFamilyMemberAdded(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        }

        @Test
        void removeVetoesWhenActiveBookingsExist() {
            UUID memberId = UUID.randomUUID();
            FamilyMember member = new FamilyMember();
            member.setId(memberId);
            member.setUser(existingUser);
            when(familyMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
            when(bookingServiceClient.getActiveBookingCount(memberId)).thenReturn(1L);

            assertThatThrownBy(() -> service.removeFamilyMember(memberId))
                    .isInstanceOf(ActiveBookingVetoException.class)
                    .hasMessageContaining("active bookings");

            verify(familyMemberRepository, never()).deleteById(any());
            verifyNoInteractions(eventProducer);
        }

        @Test
        void removeRejectsDeletionWhenBookingServiceCannotVerifyActiveBookings() {
            UUID memberId = UUID.randomUUID();
            FamilyMember member = new FamilyMember();
            member.setId(memberId);
            member.setUser(existingUser);
            when(familyMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
            when(bookingServiceClient.getActiveBookingCount(memberId))
                    .thenThrow(new ActiveBookingVetoException("Cannot verify active bookings"));

            assertThatThrownBy(() -> service.removeFamilyMember(memberId))
                    .isInstanceOf(ActiveBookingVetoException.class)
                    .hasMessageContaining("Cannot verify active bookings");

            verify(familyMemberRepository, never()).deleteById(any());
            verifyNoInteractions(eventProducer);
        }

        @Test
        void removeDeletesAndPublishesWhenNoActiveBookings() {
            UUID memberId = UUID.randomUUID();
            FamilyMember member = new FamilyMember();
            member.setId(memberId);
            member.setFirstName("Kid");
            member.setLastName("Smith");
            member.setUser(existingUser);
            when(familyMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
            when(bookingServiceClient.getActiveBookingCount(memberId)).thenReturn(0L);

            service.removeFamilyMember(memberId);

            verify(familyMemberRepository).deleteById(memberId);
            ArgumentCaptor<FamilyMemberRemovedPayload> captor = ArgumentCaptor.forClass(FamilyMemberRemovedPayload.class);
            verify(eventProducer).publishFamilyMemberRemoved(captor.capture());
            assertThat(captor.getValue().getFamilyMemberId()).isEqualTo(memberId);
        }
    }

    @Nested
    class Caregivers {

        @Test
        void createRejectsDuplicateEmail() {
            when(caregiverRepository.existsByEmail("care@example.com")).thenReturn(true);

            assertThatThrownBy(() -> service.createCaregiver("Carla", "Care", "care@example.com", "555"))
                    .hasMessageContaining("already exists");

            verify(caregiverRepository, never()).save(any());
        }

        @Test
        void createSucceeds() {
            when(caregiverRepository.existsByEmail("care@example.com")).thenReturn(false);
            when(caregiverRepository.save(any(Caregiver.class))).thenAnswer(inv -> inv.getArgument(0));

            Caregiver result = service.createCaregiver("Carla", "Care", "care@example.com", "555");

            assertThat(result.getFirstName()).isEqualTo("Carla");
            assertThat(result.getEmail()).isEqualTo("care@example.com");
        }
    }

    private FamilyMember familyMemberOf(User owner) {
        FamilyMember member = new FamilyMember();
        member.setId(UUID.randomUUID());
        member.setUser(owner);
        return member;
    }
}
