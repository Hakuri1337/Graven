package tech.hakuri.graven.interfaces;

import net.minecraft.network.chat.Component;

public interface ChatComponentAccessor {

    void graven$addClientSystemMessage(Component message, int hash);

}
