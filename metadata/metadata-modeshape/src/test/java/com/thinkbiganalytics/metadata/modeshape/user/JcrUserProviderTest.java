/**
 * 
 */
package com.thinkbiganalytics.metadata.modeshape.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import com.thinkbiganalytics.metadata.api.MetadataAccess;
import com.thinkbiganalytics.metadata.api.user.User;
import com.thinkbiganalytics.metadata.api.user.UserGroup;
import com.thinkbiganalytics.metadata.api.user.UserProvider;
import com.thinkbiganalytics.metadata.modeshape.JcrMetadataAccess;
import com.thinkbiganalytics.metadata.modeshape.JcrTestConfig;
import com.thinkbiganalytics.metadata.modeshape.ModeShapeEngineConfig;

/**
 *
 * @author Sean Felten
 */
@SpringApplicationConfiguration(classes = { ModeShapeEngineConfig.class, JcrTestConfig.class })
public class JcrUserProviderTest extends AbstractTestNGSpringContextTests {

    @Inject
    private JcrMetadataAccess metadata;
    
    @Inject
    private UserProvider provider;
    
    @Test
    public void testCreateUsers() throws Exception {
        User.ID id = metadata.commit(() -> {
            User user1 = this.provider.createUser("user1");
            
            assertThat(user1).isNotNull();
            
            User user2 = this.provider.createUser("user2");
            user2.setDisplayName("Mr. User Two");
            
            assertThat(user2).isNotNull();
            
            User user3 = this.provider.createUser("user3");
            user3.setEnabled(false);
            
            assertThat(user2).isNotNull();
            
            return user2.getId();
        }, MetadataAccess.SERVICE);
        
        metadata.read(() -> {
            Optional<User> optional = this.provider.findUserById(id);
            
            assertThat(optional.isPresent()).isTrue();
        }, MetadataAccess.SERVICE);
    }
    
    @Test(dependsOnMethods="testCreateUsers")
    public void testFindUserByName() {
        metadata.read(() -> {
            Optional<User> optional = provider.findUserBySystemName("user2");
            
            assertThat(optional.isPresent()).isTrue();
            
            User user2 = optional.get();
            
            assertThat(user2).extracting(User::getSystemName,
                                         User::getDisplayName,
                                         User::isEnabled).containsExactly("user2", "Mr. User Two", true);
        }, MetadataAccess.SERVICE);
        
        metadata.read(() -> {
            Optional<User> optional = provider.findUserBySystemName("bogus");
            
            assertThat(optional.isPresent()).isFalse();
        }, MetadataAccess.SERVICE);
    }
    
    @Test(dependsOnMethods="testCreateUsers")
    public void testUserExists() {
        metadata.read(() -> {
            assertThat(this.provider.userExists("user1")).isTrue();
            assertThat(this.provider.userExists("bogus")).isFalse();
        }, MetadataAccess.SERVICE);
    }
    
    @Test(dependsOnMethods="testCreateUsers")
    public void testFindUsers() {
        metadata.read(() -> {
            List<User> users = StreamSupport.stream(provider.findUsers().spliterator(), false).collect(Collectors.toList());
            
            assertThat(users).hasSize(3).extracting(User::getSystemName).contains("user1", "user2", "user3");
        }, MetadataAccess.SERVICE);
    }
    
    @Test(dependsOnMethods="testFindUsers")
    public void testCreateGroup() {
        UserGroup.ID id = metadata.commit(() -> {
            UserGroup groupA = this.provider.createGroup("groupA");
            
            assertThat(groupA).isNotNull();
            
            return groupA.getId();
        }, MetadataAccess.SERVICE);
        
        metadata.read(() -> {
            Optional<UserGroup> optional = this.provider.findGroupById(id);
            
            assertThat(optional.isPresent()).isTrue();
        }, MetadataAccess.SERVICE);
    }
    
    @Test(dependsOnMethods="testCreateGroup")
    public void testFindGroupByName() {
        metadata.read(() -> {
            Optional<UserGroup> optional = provider.findGroupByName("groupA");
            
            assertThat(optional.isPresent()).isTrue();
            
            UserGroup groupA = optional.get();
            
            assertThat(groupA).extracting(UserGroup::getSystemName).containsExactly("groupA");
        }, MetadataAccess.SERVICE);
    }

    @Test(dependsOnMethods="testCreateGroup")
    public void testCreateMemberGroups() {
        metadata.commit(() -> {
            UserGroup groupA = this.provider.findGroupByName("groupA").get();
            
            UserGroup groupB = this.provider.createGroup("groupB");
            UserGroup groupC = this.provider.createGroup("groupC");
            UserGroup groupD = this.provider.createGroup("groupD");
            
            assertThat(groupA.addGroup(groupB)).isTrue();
            assertThat(groupA.addGroup(groupC)).isTrue();
            assertThat(groupC.addGroup(groupD)).isTrue();
        }, MetadataAccess.SERVICE);
    }

    @Test(dependsOnMethods="testCreateMemberGroups")
    public void testAddUsersMembers() {
        metadata.commit(() -> {
            User user1 = this.provider.findUserBySystemName("user1").get();
            User user2 = this.provider.findUserBySystemName("user2").get();
            User user3 = this.provider.findUserBySystemName("user3").get();
            UserGroup groupA = this.provider.findGroupByName("groupA").get();
            UserGroup groupB = this.provider.findGroupByName("groupB").get();
            UserGroup groupD = this.provider.findGroupByName("groupD").get();

            assertThat(groupA.addUser(user1)).isTrue();
            assertThat(groupB.addUser(user2)).isTrue();
            assertThat(groupD.addUser(user3)).isTrue();
        }, MetadataAccess.SERVICE);
    }

    @Test(dependsOnMethods="testAddUsersMembers")
    public void testGetGroups() {
        metadata.read(() -> {
            UserGroup groupA = this.provider.findGroupByName("groupA").get();
            UserGroup groupC = this.provider.findGroupByName("groupC").get();
            
            assertThat(groupA.getGroups()).hasSize(2).extracting(g -> g.getSystemName()).contains("groupB", "groupC");
            assertThat(groupC.getGroups()).hasSize(1).extracting(g -> g.getSystemName()).contains("groupD");
        });
    }
    
    @Test(dependsOnMethods="testAddUsersMembers")
    public void testGetUsers() {
        metadata.read(() -> {
            UserGroup groupA = this.provider.findGroupByName("groupA").get();
            UserGroup groupB = this.provider.findGroupByName("groupB").get();
            UserGroup groupC = this.provider.findGroupByName("groupC").get();
            UserGroup groupD = this.provider.findGroupByName("groupD").get();
            
            assertThat(groupA.getUsers()).extracting(User::getSystemName).containsExactly("user1");
            assertThat(groupB.getUsers()).extracting(User::getSystemName).containsExactly("user2");
            assertThat(groupD.getUsers()).extracting(User::getSystemName).containsExactly("user3");
            assertThat(groupC.getUsers()).hasSize(0);
        });
    }
}
