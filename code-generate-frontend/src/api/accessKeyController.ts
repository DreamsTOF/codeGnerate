// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /accessKey/cdKey */
export async function getCdKey(options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/accessKey/cdKey', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /accessKey/getInfo */
export async function getApiKey(options?: { [key: string]: any }) {
  return request<API.BaseResponseAccessKeyVo>('/accessKey/getInfo', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 PUT /accessKey/useCdKey */
export async function useCdKey(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.useCdKeyParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/accessKey/useCdKey', {
    method: 'PUT',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
