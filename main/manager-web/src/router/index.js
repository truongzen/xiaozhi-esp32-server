import Vue from 'vue'
import VueRouter from 'vue-router'

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    name: 'welcome',
    component: function () {
      return import('../views/login.vue')
    }
  },
  {
    path: '/role-config',
    name: 'RoleConfig',
    component: function () {
      return import('../views/roleConfig.vue')
    }
  },
  {
    path: '/voice-print',
    name: 'VoicePrint',
    component: function () {
      return import('../views/VoicePrint.vue')
    }
  },
  {
    path: '/login',
    name: 'login',
    component: function () {
      return import('../views/login.vue')
    }
  },
  {
    path: '/home',
    name: 'home',
    component: function () {
      return import('../views/home.vue')
    }
  },
  {
    path: '/register',
    name: 'Register',
    component: function () {
      return import('../views/register.vue')
    }
  },
  {
    path: '/retrieve-password',
    name: 'RetrievePassword',
    component: function () {
      return import('../views/retrievePassword.vue')
    }
  },
  // Device management page route
  {
    path: '/device-management',
    name: 'DeviceManagement',
    component: function () {
      return import('../views/DeviceManagement.vue')
    }
  },
  // User management route
  {
    path: '/user-management',
    name: 'UserManagement',
    component: function () {
      return import('../views/UserManagement.vue')
    }
  },
  {
    path: '/model-config',
    name: 'ModelConfig',
    component: function () {
      return import('../views/ModelConfig.vue')
    }
  },
  {
    path: '/params-management',
    name: 'ParamsManagement',
    component: function () {
      return import('../views/ParamsManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: 'Parameter Management'
    }
  },
  {
    path: '/knowledge-base-management',
    name: 'KnowledgeBaseManagement',
    component: function () {
      return import('../views/KnowledgeBaseManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: 'Knowledge Base Management'
    }
  },
  {
    path: '/server-side-management',
    name: 'ServerSideManager',
    component: function () {
      return import('../views/ServerSideManager.vue')
    },
    meta: {
      requiresAuth: true,
      title: 'Server Management'
    }
  },
  {
    path: '/ota-management',
    name: 'OtaManagement',
    component: function () {
      return import('../views/OtaManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: 'OTA Management'
    }
  },
  {
    path: '/voice-resource-management',
    name: 'VoiceResourceManagement',
    component: function () {
      return import('../views/VoiceResourceManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: 'Voice Resource Activation'
    }
  },
  {
    path: '/voice-clone-management',
    name: 'VoiceCloneManagement',
    component: function () {
      return import('../views/VoiceCloneManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: 'Voice Clone Management'
    }
  },
  {
    path: '/dict-management',
    name: 'DictManagement',
    component: function () {
      return import('../views/DictManagement.vue')
    }
  },
  {
    path: '/provider-management',
    name: 'ProviderManagement',
    component: function () {
      return import('../views/ProviderManagement.vue')
    }
  },
  // Default role template route
  {
    path: '/agent-template-management',
    name: 'AgentTemplateManagement',
    component: function () {
      return import('../views/AgentTemplateManagement.vue')
    }
  },
  // Template quick configuration route
  {
    path: '/template-quick-config',
    name: 'TemplateQuickConfig',
    component: function () {
      return import('../views/TemplateQuickConfig.vue')
    }
  },
  // Feature configuration page route
  {
    path: '/feature-management',
    name: 'FeatureManagement',
    component: function () {
      return import('../views/FeatureManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: 'Feature Configuration'
    }
  },
  // Replacement word management
  {
    path: '/replacement-word-management',
    name: 'ReplacementWordManagement',
    component: function () {
      return import('../views/ReplacementWordManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: 'Replacement Word Management'
    }
  },
  // Address book management page route
  {
    path: '/address-book-management',
    name: 'AddressBookManagement',
    component: function () {
      return import('../views/AddressBookManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: 'Address Book Management'
    }
  },
]
const router = new VueRouter({
  base: process.env.VUE_APP_PUBLIC_PATH || '/',
  routes
})

// Handle duplicate navigation globally by reloading the page
const originalPush = VueRouter.prototype.push
VueRouter.prototype.push = function push(location) {
  return originalPush.call(this, location).catch(err => {
    if (err.name === 'NavigationDuplicated') {
      // On duplicate navigation, reload the page
      window.location.reload()
    } else {
      // Rethrow other errors
      throw err
    }
  })
}

// Routes that require login
const protectedRoutes = ['home', 'RoleConfig', 'DeviceManagement', 'UserManagement', 'ModelConfig', 'KnowledgeBaseManagement', 'KnowledgeFileUpload', 'AddressBookManagement']

// Route guard
router.beforeEach((to, from, next) => {
  // Check whether the route is protected
  if (protectedRoutes.includes(to.name)) {
    // Read the token from localStorage
    const token = localStorage.getItem('token')
    if (!token) {
      // Not logged in: redirect to the login page
      next({ name: 'login', query: { redirect: to.fullPath } })
      return
    }
  }
  next()
})

export default router
