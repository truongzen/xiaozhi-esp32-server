<template>
  <CustomDialog
    :visible.sync="dialogVisible"
    :title="$t('modelConfigDialog.addModel')"
    width="960px"
    size="large"
    class="add-model-dialog"
    :confirmLoading="saving"
    @close="handleClose"
    @confirm="confirm"
  >
    <div class="dialog-content">
      <!-- Model information section -->
      <div class="section-header">
        <div class="section-title">{{ $t('modelConfigDialog.modelInfo') }}</div>
        <div class="switch-group">
          <div class="switch-item">
            <span>{{ $t('modelConfigDialog.enable') }}</span>
            <el-switch v-model="formData.isEnabled" class="custom-switch"></el-switch>
          </div>
          <div class="switch-item" style="display: none;">
            <span>{{ $t('modelConfigDialog.setDefault') }}</span>
            <el-switch v-model="formData.isDefault" class="custom-switch"></el-switch>
          </div>
        </div>
      </div>

      <div class="divider"></div>
      <el-form :model="formData" label-width="auto" label-position="left" class="custom-form">
        <div class="form-row">
          <el-form-item :label="$t('modelConfigDialog.modelId')" prop="id" style="flex: 1;">
            <el-input v-model="formData.id" :placeholder="$t('modelConfigDialog.enterModelId')" class="custom-input-bg"
              maxlength="32"></el-input>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item :label="$t('modelConfigDialog.modelName')" prop="modelName" style="flex: 1;">
            <el-input v-model="formData.modelName" :placeholder="$t('modelConfigDialog.enterModelName')"
              class="custom-input-bg"></el-input>
          </el-form-item>
          <el-form-item :label="$t('modelConfigDialog.modelCode')" prop="modelCode" style="flex: 1;">
            <el-input v-model="formData.modelCode" :placeholder="$t('modelConfigDialog.enterModelCode')"
              class="custom-input-bg"></el-input>
          </el-form-item>
        </div>

        <div class="form-row">
          <el-form-item :label="$t('modelConfigDialog.supplier')" prop="supplier" style="flex: 1;">
            <el-select v-model="formData.supplier" :placeholder="$t('modelConfigDialog.selectSupplier')"
              class="custom-select custom-input-bg" style="width: 100%;" @focus="loadProviders" filterable>
              <el-option v-for="item in providers" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('modelConfigDialog.sortOrder')" prop="sortOrder" style="flex: 1;">
            <el-input v-model="formData.sort" type="number" :placeholder="$t('modelConfigDialog.enterSortOrder')"
              class="custom-input-bg"></el-input>
          </el-form-item>
        </div>

        <el-form-item :label="$t('modelConfigDialog.docLink')" prop="docLink" style="margin-bottom: 27px;">
          <el-input v-model="formData.docLink" :placeholder="$t('modelConfigDialog.enterDocLink')"
            class="custom-input-bg"></el-input>
        </el-form-item>

        <el-form-item :label="$t('modelConfigDialog.remark')" prop="remark" class="prop-remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3"
            :placeholder="$t('modelConfigDialog.enterRemark')" :autosize="{ minRows: 3, maxRows: 5 }"
            class="custom-input-bg"></el-input>
        </el-form-item>
      </el-form>

      <teleport v-if="chunkedCallInfoFields.length">
        <div class="section-title">{{ $t('modelConfigDialog.callInfo') }}</div>
        <div class="divider"></div>
      </teleport>

      <el-form :model="formData.configJson" label-width="auto" label-position="left" class="custom-form">
        <div v-for="(row, rowIndex) in chunkedCallInfoFields" :key="rowIndex" class="form-row">
          <el-form-item v-for="field in row" :key="field.prop" :label="field.label" :prop="field.prop" style="flex: 1;">
            <el-input v-model="formData.configJson[field.prop]" :placeholder="field.placeholder"
              :type="field.type || 'text'" class="custom-input-bg" :show-password="field.type === 'password'">
            </el-input>
          </el-form-item>
        </div>
      </el-form>
    </div>
  </CustomDialog>
</template>

<script>
import Api from '@/apis/api';
import CustomDialog from './CustomDialog.vue';
export default {
  name: 'AddModelDialog',
  components: {
    CustomDialog
  },
  props: {
    visible: { type: Boolean, required: true },
    modelType: { type: String, required: true }
  },
  data() {
    return {
      saving: false,
      providers: [],
      dialogVisible: false,
      providersLoaded: false,
      providerFields: [],
      currentProvider: null,
      formData: {
        id: '',
        modelName: '',
        modelCode: '',
        supplier: '',
        sort: 1,
        docLink: '',
        remark: '',
        isEnabled: true,
        isDefault: true,
        configJson: {}
      }
    }
  },
  watch: {
    visible(val) {
      this.dialogVisible = val;
      if (val) {
        this.initConfigJson();
      } else {
        this.resetForm();
      }
    },
    'formData.supplier'(newVal) {
      this.currentProvider = this.providers.find(p => p.value === newVal);
      this.providerFields = this.currentProvider?.fields || [];
      this.initDynamicConfig();
    }
  },
  computed: {
    dynamicCallInfoFields() {
      return this.providerFields;
    },
    chunkedCallInfoFields() {
      const chunkSize = 2;
      const result = [];
      for (let i = 0; i < this.dynamicCallInfoFields.length; i += chunkSize) {
        result.push(this.dynamicCallInfoFields.slice(i, i + chunkSize));
      }
      return result;
    }
  },
  methods: {
    loadProviders() {
      if (this.providersLoaded)
        return

      Api.model.getModelProviders(this.modelType, (data) => {
        this.providers = data.map(item => ({
          label: item.name,
          value: item.providerCode,
          fields: JSON.parse(item.fields || '[]').map(f => ({
            label: f.label,
            prop: f.key,
            type: f.type === 'password' ? 'password' : 'text',
            placeholder: this.$t('mcpToolCall.inputPlaceholder', { label: f.key })
          }))
        }))
        this.providersLoaded = true
      })
    },
    initConfigJson() {
      const defaultConfig = {};
      this.providerFields.forEach(field => {
        defaultConfig[field.prop] = '';
      });
      this.formData.configJson = { ...defaultConfig };
    },

    handleClose() {
      this.saving = false;
      this.dialogVisible = false;
      this.$emit('update:visible', false);
    },
    initDynamicConfig() {
      const newConfig = {};
      this.providerFields.forEach(field => {
        newConfig[field.prop] = this.formData.configJson[field.prop] || '';
      });
      this.formData.configJson = newConfig;
    },
    confirm() {
      this.saving = true;

      // Validate that model ID is not pure text or whitespace
      if (this.formData.id && !this.validateModelId(this.formData.id)) {
        this.$message.error(this.$t('modelConfigDialog.invalidModelId'));
        this.saving = false;
        return;
      }

      if (!this.formData.supplier) {
        this.$message.error(this.$t('addModelDialog.requiredSupplier'));
        this.saving = false;
        return;
      }

      const submitData = {
        id: this.formData.id || '',
        modelName: this.formData.modelName || '',
        modelCode: this.formData.modelCode || '',
        supplier: this.formData.supplier,
        sort: this.formData.sort || 1,
        docLink: this.formData.docLink || '',
        remark: this.formData.remark || '',
        isEnabled: this.formData.isEnabled ? 1 : 0,
        isDefault: this.formData.isDefault ? 1 : 0,
        provideCode: this.formData.supplier,
        configJson: {
          ...this.formData.configJson,
          type: this.formData.supplier
        }
      };

      try {
        this.$emit('confirm', submitData);
        this.handleClose();
        this.resetForm();
      } catch (e) {
        console.error(e);
      } finally {
        this.saving = false;
      }
    },
    resetForm() {
      this.saving = false;
      this.formData = {
        id: '',
        modelName: '',
        modelCode: '',
        supplier: '',
        sort: 1,
        docLink: '',
        remark: '',
        isEnabled: true,
        isDefault: true,
        configJson: {}
      };
      // Reset loading state
      this.providers = [];
      this.providersLoaded = false;
      // Reset field configuration
      this.providerFields = [];
      this.currentProvider = null;
    },
    
    // Validate model ID: must not be pure text or whitespace
    validateModelId(modelId) {
      if (!modelId || typeof modelId !== 'string') {
        return false;
      }
      
      // Trim leading and trailing whitespace
      const trimmedId = modelId.trim();
      
      // Check whether it is empty or whitespace only
      if (trimmedId === '') {
        return false;
      }
      
      // Check whether it contains only letters (pure text)
      if (/^[a-zA-Z]+$/.test(trimmedId)) {
        return false;
      }
      
      // Check whether it contains spaces
      if (/\s/.test(trimmedId)) {
        return false;
      }
      
      // Allow letters, digits, underscores and hyphens
      if (!/^[a-zA-Z0-9_-]+$/.test(trimmedId)) {
        return false;
      }
      
      return true;
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/global.scss';

::v-deep .el-dialog {
  margin-top: 6vh !important;
}
::v-deep .el-dialog__body {
  max-height: 60vh;
  overflow-y: auto;
  @include scrollbar-style;
}
.add-model-dialog {
  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .section-title {
    margin-bottom: 10px;
    font-size: 20px;
    font-weight: bold;
    color: #3d4566;
    text-align: left;
  }

  .switch-group {
    display: flex;
    align-items: center;
    gap: 20px;
  }

  .switch-item {
    display: flex;
    align-items: center;

    span {
      margin-right: 8px;
    }
  }

  .divider {
    height: 1px;
    background: #e9e9e9;
    margin-bottom: 16px;
  }

  .form-row {
    display: flex;
    gap: 20px;
    margin-bottom: 0;
  }

  .dialog-footer {
    display: flex;
    justify-content: center;
    gap: 20px;
    padding: 16px 20px;
  }
}

.custom-form .el-form-item {
  margin-bottom: 10px;
}

.custom-switch .el-switch__core {
  border-radius: 20px;
  height: 23px;
  background-color: #c0ccda;
  width: 35px;
  padding: 0 20px;
}

.custom-switch .el-switch__core:after {
  width: 15px;
  height: 15px;
  background-color: white;
  top: 3px;
  left: 4px;
  transition: all 0.3s;
}

.custom-switch.is-checked .el-switch__core {
  border-color: #b5bcf0;
  background-color: #cfd7fa;
  padding: 0 20px;
}

.custom-switch.is-checked .el-switch__core:after {
  left: 100%;
  margin-left: -18px;
  background-color: #1b47ee;
}

.custom-input-bg .el-input__inner {
  height: 32px;
}
::v-deep .el-input__inner {
  height: 32px;
}
</style>