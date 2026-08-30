import { defineStore, acceptHMRUpdate } from 'pinia'
import {
  acceptKnowledgeInvitation,
  listKnowledgeInvitations,
  readKnowledgeInvitation,
  rejectKnowledgeInvitation,
} from '../api/knowledge'

export const useMessageStore = defineStore('messages', {
  state: () => ({
    invitations: [],
    loading: false,
  }),
  getters: {
    unreadCount: (state) => state.invitations.filter((item) => item.direction === 'incoming' && !item.readTime).length,
  },
  actions: {
    clear() {
      this.invitations = []
      this.loading = false
    },
    async loadInvitations() {
      if (!localStorage.getItem('token')) {
        this.clear()
        return []
      }
      this.loading = true
      try {
        this.invitations = await listKnowledgeInvitations()
        return this.invitations
      } finally {
        this.loading = false
      }
    },
    async markRead(id) {
      await readKnowledgeInvitation(id)
      const item = this.invitations.find((row) => String(row.id) === String(id))
      if (item && !item.readTime) {
        item.readTime = new Date().toISOString()
      }
    },
    async accept(id) {
      const knowledgeId = await acceptKnowledgeInvitation(id)
      await this.loadInvitations()
      return knowledgeId
    },
    async reject(id) {
      await rejectKnowledgeInvitation(id)
      await this.loadInvitations()
    },
  },
})

if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useMessageStore, import.meta.hot))
}
