import { goToPage } from "@/utils";
import Vue from 'vue';
import Vuex from 'vuex';
import Api from '../apis/api';
import Constant from '../utils/constant';

Vue.use(Vuex)

export default new Vuex.Store({
  state: {
    token: '',
    userInfo: {}, // User info
    pubConfig: { // Public configuration
      version: '',
      beianIcpNum: 'null',
      beianGaNum: 'null',
      allowUserRegister: false,
      sm2PublicKey: ''
    }
  },
  getters: {
    getToken(state) {
      if (!state.token) {
        state.token = localStorage.getItem('token')
      }
      return state.token
    },
    getUserInfo(state) {
      return state.userInfo
    },
    getPubConfig(state) {
      return state.pubConfig
    }
  },
  mutations: {
    setToken(state, token) {
      state.token = token
      localStorage.setItem('token', token)
    },
    setUserInfo(state, userInfo) {
      state.userInfo = userInfo
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    },
    setPubConfig(state, config) {
      state.pubConfig = config
      localStorage.setItem('pubConfig', JSON.stringify(config))
    },
    clearAuth(state) {
      state.token = ''
      state.userInfo = {}
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  },
  actions: {
    // logout action
    logout({ commit }) {
      return new Promise((resolve) => {
        commit('clearAuth')
        goToPage(Constant.PAGE.LOGIN, true);
      })
    },
    // Action to fetch the public configuration
    fetchPubConfig({ commit }) {
      return new Promise((resolve) => {
        Api.user.getPubConfig(({ data }) => {
          if (data.code === 0) {
            commit('setPubConfig', data.data);
          }
          resolve();
        });
      });
    }
  },
  modules: {
  }
})