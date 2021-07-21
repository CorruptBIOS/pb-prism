package com.avairebot.commands.roblox.verification;

import com.avairebot.AvaIre;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.commands.VerificationCommand;
import com.avairebot.contracts.verification.VerificationEntity;
import com.avairebot.contracts.verification.VerificationProviders;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ReverifyCommand extends VerificationCommand {

    public ReverifyCommand(AvaIre avaire) {
        super(avaire);
    }

    @Override
    public String getName() {
        return "Reverify Command";
    }

    @Override
    public String getDescription() {
        return "Change your account being used for verification.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
                "`:command` - Set a different account on your profile for verification."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Arrays.asList(
                "`:command`"
        );
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
                "isOfficialPinewoodGuild"
        );
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("reverify", "re-verify");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        context.makeInfo("<a:loading:742658561414266890> Checking external databases... <a:loading:742658561414266890>").queue(
                unverifiedMessage -> {
                    VerificationEntity rover = avaire.getRobloxAPIManager().getVerification().callUserFromRoverAPI(context.member.getId());
                    VerificationEntity bloxlink = avaire.getRobloxAPIManager().getVerification().callUserFromBloxlinkAPI(context.member.getId());
                    VerificationEntity pinewood = avaire.getRobloxAPIManager().getVerification().callUserFromDatabaseAPI(context.member.getId());

                    List<VerificationEntity> verificationEntities = new ArrayList<>();
                    if (rover != null) {
                        verificationEntities.add(rover);
                    }
                    if (bloxlink != null) {
                        verificationEntities.add(bloxlink);
                    }
                    if (pinewood != null) {
                        verificationEntities.add(pinewood);
                    }


                    if (verificationEntities.size() < 1) {
                        unverifiedMessage.editMessageEmbeds(context.makeWarning("An account could not be found that's linked to your discord id. Please enter your Roblox name:").requestedBy(context).buildEmbed()).queue(unused -> {
                            avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class,
                                    message -> message.getMember().equals(context.getMember()) && message.getChannel().equals(context.channel),
                                    usernameMessage -> {
                                        verifyNewAccount(context, usernameMessage.getMessage().getContentRaw(), unverifiedMessage);
                                        usernameMessage.getMessage().delete().queue();
                                    }, 5, TimeUnit.MINUTES, () -> unverifiedMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 5 minutes, the verification system has been stopped.").buildEmbed()).queue());
                        });
                        return;
                    }

                    SelectionMenu.Builder menu = SelectionMenu.create("menu:provider-to-verify-with" + ":" + context.getMember().getId() + ":" + context.getMessage().getId())
                            .setPlaceholder("Select the verification provider!") // shows the placeholder indicating what this menu is for
                            .addOption("Verify a new account!", "verify-new-account", "Select this to verify a new account.", Emoji.fromMarkdown("<:PBST_GOD:857728071183237141>"))
                            .setRequiredRange(1, 1); // only one can be selected

                    for (VerificationEntity ve : verificationEntities) {
                        VerificationProviders provider = VerificationProviders.resolveProviderFromProvider(ve.getProvider());
                        if (provider != null) {
                            menu.addOption(ve.getRobloxUsername(), ve.getProvider() + ":" + ve.getRobloxUsername(), "Verify with " + ve.getRobloxUsername() + " from " + provider.provider, Emoji.fromMarkdown(provider.emoji));
                        }
                    }

                    unverifiedMessage.editMessageEmbeds(context.makeSuccess("Found `" + verificationEntities.size() + "` providers with your account in their database, please select the provider you want to verify with!").requestedBy(context).buildEmbed())
                            .setActionRow(menu.build()).queue(menuSelection -> {
                        avaire.getWaiter().waitForEvent(SelectionMenuEvent.class,
                                interaction -> interaction.getMember() != null && interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel) && interaction.getMessage().equals(unverifiedMessage),
                                providerSelect -> {
                                    providerSelect.deferEdit().queue();
                                    selectProvider(context, unverifiedMessage, rover, bloxlink, pinewood, providerSelect);
                                },
                                5, TimeUnit.MINUTES, () -> unverifiedMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 5 minutes, the verification system has been stopped.").buildEmbed()).queue());
                    });

                }
        );
        return true;
    }

    private void selectProvider(CommandMessage context, Message unverifiedMessage, VerificationEntity ve, VerificationEntity bloxlink, VerificationEntity pinewood, SelectionMenuEvent providerSelect) {
        if (providerSelect.getSelectedOptions() != null) {
            for (SelectOption so : providerSelect.getSelectedOptions()) {
                if (so.getValue().equals("verify-new-account")) {
                    unverifiedMessage.editMessageEmbeds(context.makeWarning("You selected the option to verify with a new account\n**Please enter the Roblox name of said account**:").requestedBy(context).buildEmbed()).setActionRows(Collections.emptyList()).queue(unused -> {
                        avaire.getWaiter().waitForEvent(GuildMessageReceivedEvent.class,
                                message -> message.getMember() != null && message.getMember().equals(context.getMember()) && message.getChannel().equals(context.channel),
                                usernameMessage -> {
                                    verifyNewAccount(context, usernameMessage.getMessage().getContentRaw(), unverifiedMessage);
                                    usernameMessage.getMessage().delete().queue();
                                });
                    });
                    return;
                }

                if (ve != null && so.getValue().equals("rover" + ":" + ve.getRobloxUsername())) {
                    addAccountToDatabase(context, ve.getRobloxId(), unverifiedMessage);
                    return;
                }
                if (bloxlink != null && so.getValue().equals("bloxlink" + ":" + bloxlink.getRobloxUsername())) {
                    addAccountToDatabase(context, bloxlink.getRobloxId(), unverifiedMessage);
                    return;
                }
                if (pinewood != null && so.getValue().equals("pinewood" + ":" + pinewood.getRobloxUsername())) {
                    addAccountToDatabase(context, pinewood.getRobloxId(), unverifiedMessage);
                    return;
                }
            }
        }
    }


    private void verifyNewAccount(CommandMessage context, String robloxUsername, Message originalMessage) {
        Long robloxId = getRobloxId(robloxUsername);
        if (robloxId == null) {
            context.makeError("Verification failed. Username doesn't exist on roblox. (`:username`)").set("username", robloxUsername).queue();
            return;
        }


        SelectionMenu menu = SelectionMenu.create("menu:method-to-verify-with" + ":" + context.getMember().getId() + ":" + context.getMessage().getId())
                .setPlaceholder("Select the verification method!") // shows the placeholder indicating what this menu is for
                .setRequiredRange(1, 1) // only one can be selected
                .addOption("In-game Verification", "game-verification", "Join a game on roblox to verify!", Emoji.fromUnicode("\uD83D\uDC68\u200D\uD83D\uDE80"))
                .addOption("Edit Description", "edit-description", "Add text to your profile description!", Emoji.fromMarkdown("<:roblox:863179377080401960>"))
                .build();

        originalMessage.editMessageEmbeds(context.makeInfo("Account was found on roblox, how would you like to verify?").requestedBy(context).buildEmbed())
                .setActionRow(menu).queue(m -> avaire.getWaiter().waitForEvent(SelectionMenuEvent.class,
                interaction -> interaction.getMember() != null && interaction.getMember().equals(context.getMember()) && interaction.getChannel().equals(context.channel) && interaction.getMessage().equals(originalMessage),
                accountSelect -> accountSelect.deferEdit().queue(k -> selectAccount(context, originalMessage, robloxId, accountSelect))
                , 5, TimeUnit.MINUTES, () -> originalMessage.editMessage(context.member.getAsMention()).setEmbeds(context.makeError("No response received after 5 minutes, the verification system has been stopped.").buildEmbed()).queue()));
    }

    private void selectAccount(CommandMessage context, Message originalMessage, Long robloxId, SelectionMenuEvent accountSelect) {
        if (accountSelect.getSelectedOptions() != null) {
            for (SelectOption so : accountSelect.getSelectedOptions()) {
                if (so.getValue().equals("game-verification")) {
                    runGameVerification(context, originalMessage, robloxId);
                    return;
                }

                if (so.getValue().equals("edit-description")) {
                    runDescriptionVerification(context, originalMessage, robloxId);
                    return;
                }
            }
        }
    }




    public Long getRobloxId(String un) {
        try {
            return avaire.getRobloxAPIManager().getUserAPI().getIdFromUsername(un);
        } catch (Exception e) {
            return 0L;
        }
    }
}
